package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartstorm.dto.AnalysisConflict;
import com.smartstorm.dto.AnalysisGroup;
import com.smartstorm.dto.AnalysisResult;
import com.smartstorm.dto.AnalysisVO;
import com.smartstorm.entity.BoardAnalysis;
import com.smartstorm.entity.Note;
import com.smartstorm.handler.RoomSessionManager;
import com.smartstorm.mapper.BoardAnalysisMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能整理业务：
 * <ol>
 *   <li>取房间便利贴 → 拼 prompt → 调 DeepSeek 一次性分组 + 冲突检测；</li>
 *   <li>校验过滤模型返回（只保留真实存在、每组≥2 张、冲突对不重复）；</li>
 *   <li>落库 board_analysis（供第四阶段回放）；</li>
 *   <li>通过 WebSocket 把结果广播给房间所有在线成员。</li>
 * </ol>
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 单次参与分析的便利贴上限（保护 prompt 长度与 WS 64KB 缓冲） */
    private static final int MAX_NOTES = 80;
    /** 单张便利贴正文送入模型的最大长度 */
    private static final int MAX_CONTENT_LEN = 300;
    private static final int MAX_GROUPS = 30;
    private static final int MAX_CONFLICTS = 60;

    private static final String SYSTEM_PROMPT =
            "你是 SmartStorm 头脑风暴画布的智能整理助手。我会给你当前房间内所有便利贴的 JSON（每张含 id 与 content）。\n"
            + "请做三件事：\n"
            + "1) 分组：把语义相关 / 同一主题的便利贴分成一组。每组至少 2 张；单张不成组就忽略；某张找不到归属就不放进任何组。\n"
            + "2) 冲突：找出内容上互相矛盾 / 冲突的便利贴对（例如时间、日期、方案、数字、结论不一致），说明原因。\n"
            + "3) 概括：给整面白板一句话总结（可为空字符串），给每组一个简短中文主题名和一句话总结。\n"
            + "只输出一个合法 JSON 对象，不要 Markdown、不要代码块标记、不要任何多余文字。\n"
            + "结构必须是：\n"
            + "{\"summary\":\"整板一句话\",\"groups\":[{\"name\":\"主题\",\"noteIds\":[数字id],\"summary\":\"该组一句话\"}],\"conflicts\":[{\"noteA\":数字id,\"noteB\":数字id,\"reason\":\"矛盾原因\"}]}";

    private final NoteService noteService;
    private final BoardAnalysisMapper analysisMapper;
    private final DeepSeekService deepSeekService;
    private final RoomSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public AnalysisService(NoteService noteService,
                           BoardAnalysisMapper analysisMapper,
                           DeepSeekService deepSeekService,
                           RoomSessionManager sessionManager,
                           ObjectMapper objectMapper) {
        this.noteService = noteService;
        this.analysisMapper = analysisMapper;
        this.deepSeekService = deepSeekService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    /** 执行一次智能整理并广播。operatorUserId 为发起人（需登录）。 */
    public AnalysisVO analyze(Long roomId, Long operatorUserId) {
        // 1. 取参与分析的便利贴（含正文、有内容优先）
        Map<Long, String> selected = collectNotes(roomId);
        if (selected.size() < 2) {
            throw new IllegalArgumentException("房间里的便利贴太少，先多写几张再整理");
        }
        int total = selected.size();
        if (selected.size() > MAX_NOTES) {
            log.info("便利贴数量 {} 超过上限 {}，仅分析前 {} 张", total, MAX_NOTES, MAX_NOTES);
        }

        // 2. 拼 user content 并调用 DeepSeek（偶发空结果时自动重试一次）
        String userContent = buildUserContent(selected);
        AnalysisResult result = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            JsonNode node = deepSeekService.chatJson(SYSTEM_PROMPT, userContent);
            AnalysisResult r = parseAndValidate(node, selected.keySet());
            boolean empty = r.getGroups().isEmpty() && r.getConflicts().isEmpty();
            if (!empty || attempt == 2) {
                result = r;
                break;
            }
            log.warn("智能整理第 {} 次返回空结果（groups/conflicts 均空），自动重试", attempt);
        }

        // 4. 落库
        BoardAnalysis entity = new BoardAnalysis();
        entity.setRoomId(roomId);
        entity.setUserId(operatorUserId == null ? 0L : operatorUserId);
        entity.setNoteCount(selected.size());
        entity.setModel(deepSeekService.getModel());
        try {
            entity.setResultJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException("智能整理结果序列化失败", e);
        }
        analysisMapper.insert(entity);
        BoardAnalysis saved = analysisMapper.selectById(entity.getId());

        // 5. 组装 VO + 广播
        AnalysisVO vo = toVO(saved, result);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("type", "analysis");
        out.set("data", objectMapper.valueToTree(vo));
        sessionManager.broadcastToAll(roomId, out.toString());
        log.info("智能整理完成 roomId={}, analysisId={}, notes={}, groups={}, conflicts={}",
                roomId, vo.getId(), vo.getNoteCount(),
                vo.getGroups().size(), vo.getConflicts().size());
        return vo;
    }

    /** 最近一次智能整理（无则 null）。房间刷新/晚加入恢复覆盖层用。 */
    public AnalysisVO latest(Long roomId) {
        BoardAnalysis entity = analysisMapper.selectOne(new LambdaQueryWrapper<BoardAnalysis>()
                .eq(BoardAnalysis::getRoomId, roomId)
                .orderByDesc(BoardAnalysis::getCreatedAt)
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        try {
            AnalysisResult result = objectMapper.readValue(entity.getResultJson(), AnalysisResult.class);
            return toVO(entity, result);
        } catch (Exception e) {
            log.error("解析最近一次智能整理结果失败 roomId={}, id={}", roomId, entity.getId(), e);
            return null;
        }
    }

    // ---------------- 内部 ----------------

    /** 取出有正文的便利贴，保序（按 zIndex），截断超长正文 */
    private Map<Long, String> collectNotes(Long roomId) {
        Map<Long, String> map = new LinkedHashMap<>();
        List<Note> notes = noteService.listByRoom(roomId);
        for (Note n : notes) {
            if (n.getId() == null || n.getContent() == null) {
                continue;
            }
            String content = n.getContent().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (content.length() > MAX_CONTENT_LEN) {
                content = content.substring(0, MAX_CONTENT_LEN);
            }
            map.put(n.getId(), content);
            if (map.size() >= MAX_NOTES) {
                break;
            }
        }
        return map;
    }

    private String buildUserContent(Map<Long, String> selected) {
        ArrayNode notes = objectMapper.createArrayNode();
        selected.forEach((id, content) -> {
            ObjectNode n = notes.addObject();
            n.put("id", id);
            n.put("content", content);
        });
        ObjectNode root = objectMapper.createObjectNode();
        root.put("task", "请整理以下便利贴（分组 + 冲突检测）");
        root.set("notes", notes);
        return root.toString();
    }

    private AnalysisResult parseAndValidate(JsonNode node, Set<Long> validIds) {
        AnalysisResult result = new AnalysisResult();
        result.setSummary(node.path("summary").asText(""));

        // groups
        List<AnalysisGroup> groups = new ArrayList<>();
        JsonNode groupsNode = node.path("groups");
        if (groupsNode.isArray()) {
            for (JsonNode g : groupsNode) {
                if (groups.size() >= MAX_GROUPS) {
                    break;
                }
                List<Long> noteIds = new ArrayList<>();
                Set<Long> seen = new HashSet<>();
                JsonNode ids = g.path("noteIds");
                if (ids.isArray()) {
                    for (JsonNode idNode : ids) {
                        long id = idNode.asLong(-1);
                        if (id > 0 && validIds.contains(id) && seen.add(id)) {
                            noteIds.add(id);
                        }
                    }
                }
                if (noteIds.size() < 2) {
                    continue; // 单张不成组
                }
                AnalysisGroup group = new AnalysisGroup();
                group.setNoteIds(noteIds);
                String name = g.path("name").asText("").trim();
                group.setName(name.isEmpty() ? "主题" + (groups.size() + 1) : name);
                group.setSummary(g.path("summary").asText("").trim());
                groups.add(group);
            }
        }
        result.setGroups(groups);

        // conflicts（去自指 / 去重，只保留两侧都存在的便利贴）
        List<AnalysisConflict> conflicts = new ArrayList<>();
        Set<String> pairKeys = new HashSet<>();
        JsonNode conflictsNode = node.path("conflicts");
        if (conflictsNode.isArray()) {
            for (JsonNode c : conflictsNode) {
                if (conflicts.size() >= MAX_CONFLICTS) {
                    break;
                }
                long a = c.path("noteA").asLong(-1);
                long b = c.path("noteB").asLong(-1);
                if (a <= 0 || b <= 0 || a == b || !validIds.contains(a) || !validIds.contains(b)) {
                    continue;
                }
                String key = Math.min(a, b) + ":" + Math.max(a, b);
                if (!pairKeys.add(key)) {
                    continue;
                }
                AnalysisConflict conflict = new AnalysisConflict();
                conflict.setNoteA(a);
                conflict.setNoteB(b);
                String reason = c.path("reason").asText("").trim();
                conflict.setReason(reason.isEmpty() ? "两便利贴内容相互矛盾" : reason);
                conflicts.add(conflict);
            }
        }
        result.setConflicts(conflicts);
        return result;
    }

    private AnalysisVO toVO(BoardAnalysis entity, AnalysisResult result) {
        AnalysisVO vo = new AnalysisVO();
        vo.setId(entity.getId());
        vo.setRoomId(entity.getRoomId());
        vo.setNoteCount(entity.getNoteCount());
        vo.setModel(entity.getModel());
        LocalDateTime createdAt = entity.getCreatedAt();
        vo.setCreatedAt(createdAt == null ? "" : createdAt.format(FMT));
        vo.setSummary(result.getSummary());
        vo.setGroups(result.getGroups());
        vo.setConflicts(result.getConflicts());
        return vo;
    }
}
