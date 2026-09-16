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

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 操作日志引擎。
 *
 * <p>职责：
 * <ol>
 *   <li>为每个操作分配房间内递增 seq（顺序保证）；</li>
 *   <li>算出链式哈希 {@code prev_hash} / {@code hash}，写入 op_log（一致性 + 回放数据源 + 存证）；</li>
 *   <li>将操作应用到 note 当前状态表，并返回富化后的数据（含数据库生成的 id）。</li>
 * </ol>
 *
 * <p><b>并发</b>：所有写路径都在房间级条带锁内执行。这既修掉了原先"读 max(seq) 再加一"
 * 的重号问题（并发下会丢操作），也保证画布状态与哈希链不会分叉。</p>
 */
@Service
public class OpService {

    private static final Logger log = LoggerFactory.getLogger(OpService.class);

    /**
     * 房间级条带锁，固定 64 条。
     *
     * <p>不用 {@code ConcurrentHashMap<Long, Lock>} 是因为那样每来一个新房间就会留下一个
     * 永不清除的锁对象，房间多了内存泄漏。条带锁的锁对象数是常量，两个房间偶尔共用一条
     * 只是轻微争用，不影响正确性。</p>
     */
    private static final int LOCK_STRIPES = 64;

    private final Object[] roomLocks = new Object[LOCK_STRIPES];

    private final OpLogMapper opLogMapper;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper;
    private final HashChainService hashChainService;

    public OpService(OpLogMapper opLogMapper, NoteMapper noteMapper, ObjectMapper objectMapper,
                     HashChainService hashChainService) {
        this.opLogMapper = opLogMapper;
        this.noteMapper = noteMapper;
        this.objectMapper = objectMapper;
        this.hashChainService = hashChainService;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            roomLocks[i] = new Object();
        }
    }

    /** {@link #applyAndRecord} 的结果：富化后的广播载荷 + 落库的操作日志 */
    public record OpResult(ObjectNode payload, OpLog op) {
    }

    private Object lockFor(Long roomId) {
        return roomLocks[Math.floorMod(Long.hashCode(roomId == null ? 0L : roomId), LOCK_STRIPES)];
    }

    /**
     * 在房间写锁内执行一段逻辑。
     *
     * <p>存量哈希回填用它：回填期间不能让新操作插进来，否则新操作会取到还没回填的
     * 上一行为链头（null），凭空造出一条断链。</p>
     */
    public <T> T runExclusive(Long roomId, Supplier<T> action) {
        synchronized (lockFor(roomId)) {
            return action.get();
        }
    }

    /**
     * 应用一次画布操作：更新 note 当前状态 + 写 op_log（含链式哈希），全程持房间写锁。
     *
     * <p><b>两步必须同锁。</b>只锁"记日志"那一步的话，并发下会出现"便利贴状态改了但没
     * 记日志"，画布与哈希链从此分叉，而分叉是事后无法对账的。</p>
     *
     * @return null 表示未知操作类型，调用方应忽略
     */
    public OpResult applyAndRecord(Long roomId, Long userId, String type, JsonNode data) {
        synchronized (lockFor(roomId)) {
            // 先定好 seq 与链头、校验链就绪，再改画布状态。
            // 顺序反了的话，链未就绪时会在 note 表留下改动却没有对应的操作日志 ——
            // 画布与哈希链就此分叉，而分叉事后无法对账。
            OpLog head = opLogMapper.getLatest(roomId);
            long seq = (head == null ? 0L : head.getSeq()) + 1;
            String prevHash = requirePrevHash(roomId, head);

            ObjectNode enriched = applyOp(roomId, userId, type, data);
            if (enriched == null) {
                return null;
            }
            OpLog op = insertOp(roomId, userId, type, enriched.toString(), seq, prevHash);
            return new OpResult(enriched, op);
        }
    }

    /**
     * 取链头哈希；房间首条返回创世哈希。
     *
     * <p>历史数据尚未回填完时<b>直接抛异常，绝不降级</b>：若用创世哈希兜底，会按错误的
     * 链头算出一条<b>永久无法修复</b>的断链 —— 因为那些操作的哈希已经按错的 prev_hash
     * 算死，事后无从修正。而且这种损坏没有任何外部征兆，直到某天有人跑 verify 才发现。
     * 宁可当场失败。</p>
     */
    private String requirePrevHash(Long roomId, OpLog head) {
        if (head == null) {
            return HashChainService.GENESIS;
        }
        if (head.getHash() == null) {
            throw new IllegalStateException(
                    "房间 " + roomId + " 的历史操作尚未完成哈希回填（seq=" + head.getSeq()
                            + " 没有哈希），拒绝写入以免产生无法修复的断链；请查看启动日志中的回填错误");
        }
        return head.getHash();
    }

    /**
     * 落库一条带链式哈希的操作日志。
     *
     * <p>调用方必须已持有房间写锁（见 {@link #applyAndRecord} 与 {@link #runExclusive}），
     * 且 seq 与 prevHash 必须是在同一临界区内取定的。</p>
     */
    private OpLog insertOp(Long roomId, Long userId, String type, String payload,
                           long seq, String prevHash) {
        // MySQL DATETIME 只到秒精度。这里必须截断纳秒：否则写入时算的哈希带着纳秒，
        // 而日后 verify 从库里读回的值纳秒已被截断，两者对不上，链会永远验不过。
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);

        OpLog op = new OpLog();
        op.setRoomId(roomId);
        op.setUserId(userId);
        op.setType(type);
        op.setSeq(seq);
        op.setPayload(payload);
        op.setPrevHash(prevHash);
        op.setCreatedAt(createdAt);
        op.setHash(hashChainService.computeHash(prevHash, roomId, seq, userId, type, payload, createdAt));
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
