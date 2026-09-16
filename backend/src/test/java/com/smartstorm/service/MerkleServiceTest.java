package com.smartstorm.service;

import com.smartstorm.dto.MerkleProofVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Merkle 树的正确性测试。
 *
 * <p>纯单元测试，不起 Spring 上下文，不依赖数据库。</p>
 *
 * <p>重点是奇数叶子（提升节点）的情况 —— 那部分的下标映射最容易写错，
 * 一旦错了 proof 就永远验不过。</p>
 */
class MerkleServiceTest {

    private final MerkleService merkle = new MerkleService();

    /** 造 n 个可预测但互不相同的叶子哈希 */
    private List<String> leaves(int n) {
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(HashChainService.sha256Hex(("leaf-" + i).getBytes()));
        }
        return list;
    }

    @Test
    @DisplayName("单个叶子：根就是叶子本身，证明路径为空")
    void singleLeaf() {
        List<String> ls = leaves(1);
        assertEquals(ls.get(0), merkle.root(ls));

        List<MerkleProofVO.Step> path = merkle.proof(ls, 0);
        assertTrue(path.isEmpty(), "单叶子批次不应有证明路径");
        assertTrue(merkle.verifyProof(ls.get(0), path, merkle.root(ls)));
    }

    @Test
    @DisplayName("两个叶子：根等于两者拼接后的哈希")
    void twoLeaves() {
        List<String> ls = leaves(2);
        String expected = HashChainService.sha256Hex(concat(
                HashChainService.fromHex(ls.get(0)), HashChainService.fromHex(ls.get(1))));
        assertEquals(expected, merkle.root(ls));

        for (int i = 0; i < 2; i++) {
            assertTrue(merkle.verifyProof(ls.get(i), merkle.proof(ls, i), merkle.root(ls)),
                    "下标 " + i + " 的证明应通过");
        }
    }

    @Test
    @DisplayName("各种叶子数下，每个下标的证明路径都能验证通过（含奇数提升）")
    void proofVerifiesForEveryIndex() {
        for (int n = 1; n <= 17; n++) {
            List<String> ls = leaves(n);
            String root = merkle.root(ls);
            for (int i = 0; i < n; i++) {
                assertTrue(merkle.verifyProof(ls.get(i), merkle.proof(ls, i), root),
                        "叶子数 " + n + " 下标 " + i + " 的证明应通过");
            }
        }
    }

    @Test
    @DisplayName("同一批叶子重复计算，根必须稳定一致")
    void deterministic() {
        List<String> ls = leaves(9);
        assertEquals(merkle.root(ls), merkle.root(ls));
        assertEquals(merkle.root(ls), merkle.root(new ArrayList<>(ls)));
    }

    @Test
    @DisplayName("改动任意一个叶子，根必变")
    void tamperChangesRoot() {
        List<String> ls = leaves(6);
        String before = merkle.root(ls);

        List<String> tampered = new ArrayList<>(ls);
        tampered.set(3, HashChainService.sha256Hex("tampered".getBytes()));
        assertNotEquals(before, merkle.root(tampered), "叶子被改动后根应变化");
    }

    @Test
    @DisplayName("用别人的证明路径验证自己，必须失败")
    void wrongProofRejected() {
        List<String> ls = leaves(5);
        String root = merkle.root(ls);

        // 用下标 0 的路径去证明下标 1 的叶子 —— 应当不通过
        List<MerkleProofVO.Step> pathOf0 = merkle.proof(ls, 0);
        assertFalse(merkle.verifyProof(ls.get(1), pathOf0, root));
    }

    @Test
    @DisplayName("对不上的根，验证必须失败")
    void wrongRootRejected() {
        List<String> ls = leaves(4);
        String otherRoot = merkle.root(leaves(7));
        assertFalse(merkle.verifyProof(ls.get(0), merkle.proof(ls, 0), otherRoot));
    }

    @Test
    @DisplayName("空列表与越界下标要报错，而不是静默返回")
    void invalidInput() {
        assertThrows(IllegalArgumentException.class, () -> merkle.root(List.of()));
        assertThrows(IllegalArgumentException.class, () -> merkle.root(null));
        assertThrows(IllegalArgumentException.class, () -> merkle.proof(leaves(3), 3));
        assertThrows(IllegalArgumentException.class, () -> merkle.proof(leaves(3), -1));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
