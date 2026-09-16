package com.smartstorm.service;

import com.smartstorm.dto.MerkleProofVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkle 树：把一批操作的哈希聚成单个根，只把根锚定上链。
 *
 * <p>这是整个方案里最关键的成本决策 —— 逐条操作上链是 O(n) 次交易，而 Merkle 树把它
 * 降到 O(1)：链上只存 32 字节的根，单条操作靠 {@link #proof} 出的路径照样可证。</p>
 *
 * <p>两个约定：</p>
 * <ul>
 *   <li><b>叶子直接用操作哈希</b>，不再套一层。操作哈希本身已是结构化原像的 SHA-256
 *       输出，不会与内部节点混淆。</li>
 *   <li><b>奇数个节点时把最后一个提升到上一层</b>（RFC 6962 风格），不复制。复制是
 *       Bitcoin 的做法，有已知歧义：不同的叶子集合可能算出同一个根
 *       （参见 CVE-2012-2459 一类问题）。</li>
 * </ul>
 */
@Service
public class MerkleService {

    /** 兄弟节点在左侧 */
    public static final String LEFT = "left";
    /** 兄弟节点在右侧 */
    public static final String RIGHT = "right";

    /**
     * 计算 Merkle 根。
     *
     * @param leafHashes 叶子哈希（hex），顺序必须稳定 —— 调用方按 seq 升序传
     * @return 根哈希（hex）
     */
    public String root(List<String> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            throw new IllegalArgumentException("叶子哈希列表不能为空");
        }
        List<String> level = new ArrayList<>(leafHashes);
        while (level.size() > 1) {
            level = nextLevel(level);
        }
        return level.get(0);
    }

    /**
     * 生成第 {@code index} 个叶子的存在性证明路径。
     *
     * @param leafHashes 完整的叶子列表（必须与算根时完全一致）
     * @param index      叶子下标
     * @return 从叶子逐层到根的兄弟节点路径；单叶子批次返回空路径
     */
    public List<MerkleProofVO.Step> proof(List<String> leafHashes, int index) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            throw new IllegalArgumentException("叶子哈希列表不能为空");
        }
        if (index < 0 || index >= leafHashes.size()) {
            throw new IllegalArgumentException("叶子下标越界：" + index);
        }

        List<MerkleProofVO.Step> path = new ArrayList<>();
        List<String> level = new ArrayList<>(leafHashes);
        int idx = index;

        while (level.size() > 1) {
            List<String> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                if (i + 1 < level.size()) {
                    next.add(combine(level.get(i), level.get(i + 1)));
                    if (i == idx) {
                        path.add(step(level.get(i + 1), RIGHT));
                    } else if (i + 1 == idx) {
                        path.add(step(level.get(i), LEFT));
                    }
                } else {
                    // 奇数：最后一个被提升到上一层，它在证明路径上不产生兄弟节点
                    next.add(level.get(i));
                }
            }
            idx /= 2;
            level = next;
        }
        return path;
    }

    /**
     * 用证明路径校验某个叶子是否属于该根。
     *
     * <p>这是 Merkle 证明的验证端，前端或链上合约都可以用同样的逻辑独立复算 ——
     * 不需要访问数据库或信任后端。</p>
     */
    public boolean verifyProof(String leafHash, List<MerkleProofVO.Step> path, String root) {
        if (leafHash == null || root == null) {
            return false;
        }
        String h = leafHash;
        if (path != null) {
            for (MerkleProofVO.Step s : path) {
                if (s.getSibling() == null) {
                    return false;
                }
                h = LEFT.equals(s.getPosition())
                        ? combine(s.getSibling(), h)
                        : combine(h, s.getSibling());
            }
        }
        return h.equals(root);
    }

    // ---------------- 内部 ----------------

    /** 把当前层两两合并成上一层；落单的节点直接提升 */
    private List<String> nextLevel(List<String> level) {
        List<String> next = new ArrayList<>((level.size() + 1) / 2);
        for (int i = 0; i < level.size(); i += 2) {
            if (i + 1 < level.size()) {
                next.add(combine(level.get(i), level.get(i + 1)));
            } else {
                next.add(level.get(i));
            }
        }
        return next;
    }

    /** SHA256(left ‖ right) —— 对原始字节拼接后哈希，不是对 hex 字符串 */
    private String combine(String leftHex, String rightHex) {
        byte[] l = HashChainService.fromHex(leftHex);
        byte[] r = HashChainService.fromHex(rightHex);
        byte[] buf = new byte[l.length + r.length];
        System.arraycopy(l, 0, buf, 0, l.length);
        System.arraycopy(r, 0, buf, l.length, r.length);
        return HashChainService.sha256Hex(buf);
    }

    private MerkleProofVO.Step step(String sibling, String position) {
        MerkleProofVO.Step s = new MerkleProofVO.Step();
        s.setSibling(sibling);
        s.setPosition(position);
        return s;
    }
}
