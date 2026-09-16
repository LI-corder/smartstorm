package com.smartstorm.dto;

import lombok.Data;

import java.util.List;

/**
 * 单条操作的 Merkle 存在性证明。
 *
 * <p>用它可以证明「这条操作属于某个已锚定到链上的批次」，而不需要把整批操作都拿出来。
 * 证明路径本身不落库 —— op 数据都在 op_log 里且只追加，验证时按同一区间重新聚合
 * 就能复现出完全相同的树。</p>
 */
@Data
public class MerkleProofVO {

    /** 该操作在房间内的 seq */
    private Long seq;

    /** 该操作的哈希（叶子） */
    private String leafHash;

    /** 该批次锚定上链的 Merkle 根 */
    private String merkleRoot;

    /** 所属批次的 seq 区间 */
    private Long fromSeq;
    private Long toSeq;

    /** 链上交易哈希；批次尚未上链时为 null */
    private String txHash;

    /** 证明路径：从叶子到根，逐层给出兄弟节点 */
    private List<Step> path;

    /** 路径上的一步：兄弟节点的哈希 + 它在左边还是右边 */
    @Data
    public static class Step {

        /** 兄弟节点哈希（hex） */
        private String sibling;

        /** 兄弟节点的位置：left / right */
        private String position;
    }
}
