package com.smartstorm.dto;

import lombok.Data;

/**
 * 一个存证锚点的展示对象。
 */
@Data
public class AnchorVO {

    private Long id;

    private Long roomId;

    /** 本批覆盖的 seq 区间 */
    private Long fromSeq;
    private Long toSeq;

    /** 本批操作数 */
    private Long opCount;

    /** Merkle 根（0x 前缀） */
    private String merkleRoot;

    /** 链上交易哈希；未上链时为 null */
    private String txHash;

    /** 区块高度；未上链时为 null */
    private Long blockNumber;

    /** 状态：0待上链 1已上链 2失败 */
    private Integer status;

    /** 状态的中文说明，前端直接展示 */
    private String statusText;

    private String createdAt;

    private String confirmedAt;
}
