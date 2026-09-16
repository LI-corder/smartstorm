package com.smartstorm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 链上存证锚点：把一段 seq 区间内所有操作的 Merkle 根锚定到区块链。
 *
 * <p>这是"上链"这个动作本身 —— {@code op_log} 的哈希链只能证明"某一条没被偷改"，
 * 防不住有库权限的人把后续哈希全部重算。根一旦上链，重算就无从遁形。</p>
 */
@Data
@TableName("chain_anchor")
public class ChainAnchor {

    /** 已生成待上链 */
    public static final int STATUS_PENDING = 0;
    /** 已上链确认 */
    public static final int STATUS_CONFIRMED = 1;
    /** 上链失败，等待重试 */
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    /** 本批起始 seq */
    private Long fromSeq;

    /** 本批结束 seq */
    private Long toSeq;

    /** 本批 Merkle 根（0x 前缀） */
    private String merkleRoot;

    /** 链上交易哈希 */
    private String txHash;

    /** 区块高度 */
    private Long blockNumber;

    /** 状态：0待上链 1已上链 2失败 */
    private Integer status;

    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;
}
