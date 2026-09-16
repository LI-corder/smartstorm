package com.smartstorm.service;

import com.smartstorm.dto.ChainVerifyResult;
import com.smartstorm.entity.OpLog;
import com.smartstorm.mapper.OpLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志哈希链。
 *
 * <p>每个房间一条独立的链：
 * {@code hash_n = SHA256(prev_hash | room_id | seq | user_id | type | payload | created_at)}，
 * 首条操作的 {@code prev_hash} 为 {@link #GENESIS}（64 个 0）。</p>
 *
 * <p><b>这个类只能检测"事后偷改某一条"。</b>防不住有库权限的人改完后把后续哈希
 * 全部重算 —— 那需要把链头锚定到应用改不动的地方（见 {@code AnchorService}）。
 * 两者是一套，缺一不可。</p>
 */
@Service
public class HashChainService {

    private static final Logger log = LoggerFactory.getLogger(HashChainService.class);

    /** 创世哈希：房间首条操作的 prev_hash */
    public static final String GENESIS = "0".repeat(64);

    /**
     * 字段分隔符，取 NUL 字符（Unicode U+0000）。
     *
     * <p>之所以用 NUL：payload 是任意 JSON，用竖线之类的可见字符作分隔符存在歧义
     * （字段内容本身可能就含该字符）。而 Jackson 序列化出的文本里不可能出现裸 NUL，
     * 它会被转义成反斜杠 + u0000 共六个可见字符，所以 NUL 不会与字段内容冲突。</p>
     */
    private static final String SEP = "\0";

    /** 固定格式，保证插入与验证两次格式化结果一致 */
    private static final DateTimeFormatter HASH_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final OpLogMapper opLogMapper;

    public HashChainService(OpLogMapper opLogMapper) {
        this.opLogMapper = opLogMapper;
    }

    /**
     * 计算一条操作的链式哈希。
     *
     * <p><b>createdAt 必须是从库里读回的那个值</b>，不能重新取当前时间 ——
     * MySQL DATETIME 只到秒精度，写入时若不截断纳秒，验证时会与库里的值对不上，
     * 导致链永远验不过。</p>
     */
    public String computeHash(String prevHash, Long roomId, Long seq, Long userId,
                              String type, String payload, LocalDateTime createdAt) {
        String canonical = String.join(SEP,
                (prevHash == null || prevHash.isEmpty()) ? GENESIS : prevHash,
                String.valueOf(roomId),
                String.valueOf(seq),
                String.valueOf(userId),
                type == null ? "" : type,
                payload == null ? "" : payload,
                createdAt == null ? "" : createdAt.format(HASH_TIME_FMT));
        return sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 房间内最新一条操作的哈希（链头）。房间无操作时返回 {@link #GENESIS}。
     *
     * <p>走 {@code getLatest} 而不是 {@code listAfterSeq(roomId, 0)} —— 后者会把整个房间的
     * 操作全查出来，随操作数线性变慢，而这条每写一次操作就要调一次。</p>
     *
     * <p>正常情况下链头不可能没有哈希（回填与写操作共用同一把房间锁，不会交错）。
     * 若真的取到 null，说明该房间回填失败，此时返回 GENESIS 会造出一条断链 ——
     * 这是刻意的：让 {@code verify} 能明确报出来，好过静默产生错误的历史。</p>
     */
    public String headHash(Long roomId) {
        OpLog latest = opLogMapper.getLatest(roomId);
        if (latest == null) {
            return GENESIS;
        }
        if (latest.getHash() == null) {
            log.warn("房间 {} 最新操作 seq={} 尚未纳入哈希链，回填可能未完成", roomId, latest.getSeq());
            return GENESIS;
        }
        return latest.getHash();
    }

    /**
     * 从库里的记录重算整条链，校验完整性。
     *
     * <p>三层检查，任一不过即返回断点：</p>
     * <ol>
     *   <li><b>序号连续</b>：seq 应从 1 开始逐个递增 —— 断了说明有记录被删除；</li>
     *   <li><b>链式关系</b>：本行 prev_hash 应等于上一行的 hash —— 断了说明顺序被破坏；</li>
     *   <li><b>内容完整</b>：按本行字段重算的哈希应等于本行 hash —— 不等说明本行内容被改过。</li>
     * </ol>
     */
    public ChainVerifyResult verify(Long roomId) {
        List<OpLog> ops = opLogMapper.listAfterSeq(roomId, 0L);

        ChainVerifyResult result = new ChainVerifyResult();
        result.setTotalOps(ops.size());

        long expectedSeq = 1L;
        String expectedPrev = GENESIS;
        for (OpLog op : ops) {
            if (op.getSeq() == null || op.getSeq() != expectedSeq) {
                return result.broken(op.getSeq(),
                        "操作序号不连续，seq=" + expectedSeq + " 的记录缺失或被删除");
            }
            if (op.getHash() == null) {
                return result.broken(op.getSeq(), "该操作的哈希为空（未被纳入链）");
            }
            if (!expectedPrev.equals(op.getPrevHash())) {
                return result.broken(op.getSeq(), "链式关系断裂：本行 prev_hash 与上一条的 hash 不符");
            }
            String recomputed = computeHash(op.getPrevHash(), op.getRoomId(), op.getSeq(),
                    op.getUserId(), op.getType(), op.getPayload(), op.getCreatedAt());
            if (!recomputed.equals(op.getHash())) {
                return result.broken(op.getSeq(), "操作内容与哈希不符，该记录已被篡改");
            }
            expectedPrev = op.getHash();
            expectedSeq++;
        }
        return result.pass();
    }

    // ---------------- 哈希工具 ----------------

    public static String sha256Hex(byte[] data) {
        return toHex(sha256(data));
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 未提供 SHA-256 算法", e);
        }
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /** hex 字符串转字节数组；长度非偶数或含非法字符时抛 IllegalArgumentException */
    public static byte[] fromHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("非法的 hex 字符串");
        }
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException(
                        "非法的 hex 字符：" + hex.charAt(i) + hex.charAt(i + 1));
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
