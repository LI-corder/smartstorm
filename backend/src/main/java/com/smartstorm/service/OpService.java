package com.smartstorm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartstorm.entity.Note;
import com.smartstorm.entity.OpLog;
import com.smartstorm.mapper.NoteMapper;
import com.smartstorm.mapper.OpLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 操作日志引擎。
 *
 * <p>职责：
 * <ol>
 *   <li>为每个操作分配房间内递增 seq（顺序保证）；</li>
 *   <li>将操作写入 op_log（一致性 + 回放数据源）；</li>
 *   <li>将操作应用到 note 当前状态表，并返回富化后的数据（含数据库生成的 id）。</li>
 * </ol>
 */
@Service
public class OpService {

    private static final Logger log = LoggerFactory.getLogger(OpService.class);

    private final OpLogMapper opLogMapper;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper;

    public OpService(OpLogMapper opLogMapper, NoteMapper noteMapper, ObjectMapper objectMapper) {
        this.opLogMapper = opLogMapper;
        this.noteMapper = noteMapper;
        this.objectMapper = objectMapper;
    }

    /** 房间内下一个操作序号 */
    public long nextSeq(Long roomId) {
        Long max = opLogMapper.getMaxSeq(roomId);
        return (max == null ? 0 : max) + 1;
    }

    /** 记录一条操作日志 */
    public OpLog record(Long roomId, Long userId, String type, String payload) {
        OpLog op = new OpLog();
        op.setRoomId(roomId);
        op.setUserId(userId);
        op.setType(type);
        op.setSeq(nextSeq(roomId));
        op.setPayload(payload);
        opLogMapper.insert(op);
        return op;
    }

    /**
     * 将操作应用到 note 当前状态表，返回富化后的操作数据（含数据库生成的 id）。
     * 返回 null 表示未知操作类型，调用方应忽略。
     */
    public ObjectNode applyOp(Long roomId, Long userId, String type, JsonNode data) {
        switch (type) {
            case "add_note" -> {
                Note n = new Note();
                n.setRoomId(roomId);
                n.setUserId(userId);
                n.setX(data.path("x").asDouble(0));
                n.setY(data.path("y").asDouble(0));
                n.setWidth(data.path("width").asDouble(160));
                n.setHeight(data.path("height").asDouble(100));
                n.setColor(data.path("color").asText("yellow"));
                n.setContent(data.path("content").asText(""));
                n.setZIndex(0);
                n.setDeleted(0);
                noteMapper.insert(n);
                ObjectNode out = objectMapper.createObjectNode();
                out.put("id", n.getId());
                out.put("x", n.getX());
                out.put("y", n.getY());
                out.put("width", n.getWidth());
                out.put("height", n.getHeight());
                out.put("color", n.getColor());
                out.put("content", n.getContent());
                return out;
            }
            case "edit_note" -> {
                Long id = data.path("id").asLong();
                Note n = noteMapper.selectById(id);
                if (n == null) {
                    return null;
                }
                n.setContent(data.path("content").asText(""));
                noteMapper.updateById(n);
                ObjectNode out = objectMapper.createObjectNode();
                out.put("id", id);
                out.put("content", n.getContent());
                return out;
            }
            case "move_note" -> {
                Long id = data.path("id").asLong();
                Note n = noteMapper.selectById(id);
                if (n == null) {
                    return null;
                }
                n.setX(data.path("x").asDouble(0));
                n.setY(data.path("y").asDouble(0));
                noteMapper.updateById(n);
                ObjectNode out = objectMapper.createObjectNode();
                out.put("id", id);
                out.put("x", n.getX());
                out.put("y", n.getY());
                return out;
            }
            case "color_note" -> {
                Long id = data.path("id").asLong();
                Note n = noteMapper.selectById(id);
                if (n == null) {
                    return null;
                }
                n.setColor(data.path("color").asText("yellow"));
                noteMapper.updateById(n);
                ObjectNode out = objectMapper.createObjectNode();
                out.put("id", id);
                out.put("color", n.getColor());
                return out;
            }
            case "delete_note" -> {
                Long id = data.path("id").asLong();
                noteMapper.deleteById(id);
                ObjectNode out = objectMapper.createObjectNode();
                out.put("id", id);
                return out;
            }
            default -> {
                log.warn("未知操作类型: {}", type);
                return null;
            }
        }
    }
}
