package com.smartstorm.dto;

import lombok.Data;

/**
 * 链概览，供区块信息页顶部的统计卡展示。
 *
 * <p>链未启用时 {@code chainEnabled=false}，其余链上字段为 null —— 前端据此把数字显示成
 * "—" 而不是 0。"没连上链"和"链上没有区块"是两回事，不能混为一谈。</p>
 */
@Data
public class ChainOverviewVO {

    /** 存证链是否已启用 */
    private boolean chainEnabled;

    /** 当前区块高度 */
    private Long blockNumber;

    /** 链上交易总数 */
    private Long txCount;

    /** 链上失败交易数 */
    private Long failedTxCount;

    /** 共识节点数 */
    private Integer sealerCount;

    // ---------------- 本系统统计（不依赖链，未启用时也有值） ----------------

    /** 本系统的存证批次总数 */
    private int anchorTotal;

    /** 其中已上链确认的批次数 */
    private int anchorConfirmed;
}
