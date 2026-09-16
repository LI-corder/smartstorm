package com.smartstorm.dto;

import lombok.Data;

import java.util.List;

/**
 * 一个区块的信息。
 *
 * <p>时间由后端格式化成 {@code yyyy-MM-dd HH:mm:ss} 字符串再返回 —— 项目前端没有日期库
 * 也没有格式化工具，时间一律以可读字符串的形式从前端拿（与 `AnalysisVO.createdAt` 一致）。</p>
 */
@Data
public class ChainBlockVO {

    /** 区块高度 */
    private Long number;

    /** 区块哈希 */
    private String hash;

    /** 父区块哈希 */
    private String parentHash;

    /** 出块时间（已格式化，Asia/Shanghai） */
    private String timestamp;

    /** 出块时间（Unix 秒，前端如需相对时间可用） */
    private Long timestampSeconds;

    /** 交易笔数 */
    private int txCount;

    /** 交易列表（含本系统存证标注） */
    private List<BlockTxVO> transactions;

    /** 出块节点在共识列表中的下标（从 0 开始）；前端展示时 +1 */
    private Integer sealerIndex;

    /** 共识节点总数 */
    private Integer sealerCount;

    /** 状态根 */
    private String stateRoot;

    /** 交易根 */
    private String transactionsRoot;

    /** 区块版本 */
    private Integer version;

    /** 本区块消耗的 gas */
    private String gasUsed;
}
