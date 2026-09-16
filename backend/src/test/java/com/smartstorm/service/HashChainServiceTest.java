package com.smartstorm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 哈希链纯函数部分的测试。
 *
 * <p>只测不依赖数据库的 {@code computeHash} 与 hex 工具 —— {@code verify} 需要 mapper，
 * 留给端到端验证。</p>
 */
class HashChainServiceTest {

    /** computeHash 不碰 mapper，传 null 即可 */
    private final HashChainService chain = new HashChainService(null);

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 16, 14, 32, 1);

    private String hash(String prev, long seq, String payload) {
        return chain.computeHash(prev, 3L, seq, 5L, "move_note", payload, T);
    }

    @Test
    @DisplayName("同样的输入必须算出同样的哈希")
    void deterministic() {
        assertEquals(hash(HashChainService.GENESIS, 1, "{\"id\":7}"),
                hash(HashChainService.GENESIS, 1, "{\"id\":7}"));
    }

    @Test
    @DisplayName("prevHash 为 null 时按创世哈希处理")
    void nullPrevHashIsGenesis() {
        assertEquals(hash(HashChainService.GENESIS, 1, "{}"), hash(null, 1, "{}"));
        assertEquals(hash(HashChainService.GENESIS, 1, "{}"), hash("", 1, "{}"));
    }

    @Test
    @DisplayName("任一字段被改动，哈希必变 —— 这是篡改可检测的基础")
    void everyFieldAffectsHash() {
        String base = hash(HashChainService.GENESIS, 1, "{\"id\":7}");
        assertNotEquals(base, hash("1".repeat(64), 1, "{\"id\":7}"), "prevHash 变了哈希应变");
        assertNotEquals(base, hash(HashChainService.GENESIS, 2, "{\"id\":7}"), "seq 变了哈希应变");
        assertNotEquals(base, hash(HashChainService.GENESIS, 1, "{\"id\":8}"), "payload 变了哈希应变");

        assertNotEquals(base, chain.computeHash(HashChainService.GENESIS, 3L, 1L, 6L,
                "move_note", "{\"id\":7}", T), "userId 变了哈希应变");
        assertNotEquals(base, chain.computeHash(HashChainService.GENESIS, 3L, 1L, 5L,
                "edit_note", "{\"id\":7}", T), "type 变了哈希应变");
        assertNotEquals(base, chain.computeHash(HashChainService.GENESIS, 3L, 1L, 5L,
                "move_note", "{\"id\":7}", T.plusSeconds(1)), "createdAt 变了哈希应变");
    }

    @Test
    @DisplayName("字段边界不可混淆：分隔符必须防住拼接歧义")
    void fieldBoundariesAreUnambiguous() {
        // 用固定分隔符拼接时，("ab","c") 与 ("a","bc") 若不转义就会撞成同一个串。
        // 这里通过 type 与 payload 两个相邻字段验证不会撞。
        String a = chain.computeHash(HashChainService.GENESIS, 3L, 1L, 5L, "ab", "c", T);
        String b = chain.computeHash(HashChainService.GENESIS, 3L, 1L, 5L, "a", "bc", T);
        assertNotEquals(a, b, "相邻字段的边界必须可区分，否则可以构造出哈希碰撞");
    }

    @Test
    @DisplayName("写入前必须 withNano(0)：MySQL 会把小数秒四舍五入，读回的值就对不上了")
    void nanosMustBeTruncatedBeforeHashing() {
        // 注意：哈希格式只到秒，所以「格式化」这一步本身就会丢掉纳秒 ——
        // 单看哈希计算是发现不了问题的，这也是这个坑隐蔽的地方。
        //
        // 真正的问题在存储：MySQL 8.0 对 DATETIME(0)（秒精度）是「四舍五入」而非截断，
        // 14:32:01.999 会被存成 14:32:02。于是读回重算的哈希用的是 02 秒，
        // 而写入时算哈希用的是 01 秒 —— 链断裂，且每一行都可能中招。
        LocalDateTime almostNextSecond = LocalDateTime.of(2026, 9, 16, 14, 32, 1, 999_000_000);
        LocalDateTime nextSecond = LocalDateTime.of(2026, 9, 16, 14, 32, 2);

        assertNotEquals(
                chain.computeHash(HashChainService.GENESIS, 3L, 1L, 5L, "move_note", "{}", almostNextSecond),
                chain.computeHash(HashChainService.GENESIS, 3L, 1L, 5L, "move_note", "{}", nextSecond),
                "带 .999 纳秒写入会被 MySQL 进位到下一秒，读回重算的哈希就与写入时不一致");

        // withNano(0) 之后，Java 侧的值与数据库能存下的值完全相同，
        // 存储层不需要做任何取舍（也就无从进位），往返必然一致。
        LocalDateTime truncated = almostNextSecond.withNano(0);
        assertEquals(0, truncated.getNano());
        assertEquals(LocalDateTime.of(2026, 9, 16, 14, 32, 1), truncated);
    }

    @Test
    @DisplayName("创世哈希是 64 个 0")
    void genesisFormat() {
        assertEquals(64, HashChainService.GENESIS.length());
        assertEquals("0".repeat(64), HashChainService.GENESIS);
    }

    @Test
    @DisplayName("hex 编解码可往返")
    void hexRoundTrip() {
        byte[] data = "SmartStorm 存证".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, HashChainService.fromHex(HashChainService.toHex(data)));
    }

    @Test
    @DisplayName("非法 hex 要报错，不能静默返回错误结果")
    void invalidHex() {
        assertThrows(IllegalArgumentException.class, () -> HashChainService.fromHex("abc"));
        assertThrows(IllegalArgumentException.class, () -> HashChainService.fromHex("zz"));
        assertThrows(IllegalArgumentException.class, () -> HashChainService.fromHex(null));
    }

    @Test
    @DisplayName("SHA-256 输出为 64 位小写 hex")
    void sha256Format() {
        String h = HashChainService.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(64, h.length());
        // "abc" 的 SHA-256 是公认的测试向量
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h);
    }
}
