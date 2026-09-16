package com.smartstorm.dto;

import lombok.Data;

/**
 * 房间存证状态摘要，供前端"存证"面板展示。
 */
@Data
public class ChainStatusVO {

    private Long roomId;

    /** 存证链是否已启用。false 时前端应显示"链未连接"而不是报错 */
    private boolean chainEnabled;

    /** 该房间的操作总数 */
    private int totalOps;

    /** 当前最大 seq */
    private Long maxSeq;

    /** 已被某个批次覆盖（含尚未上链的批次）的最大 seq */
    private Long anchoredSeq;

    /** 尚未被任何批次覆盖的操作数 */
    private long unanchoredCount;

    /** 锚点总数 */
    private int anchorCount;

    /** 已上链确认的锚点数 */
    private int confirmedCount;

    /** 待上链或失败的锚点数 */
    private int pendingCount;

    /** 当前链头哈希（最新一条操作的哈希） */
    private String headHash;
}
