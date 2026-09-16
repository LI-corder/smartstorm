package com.smartstorm.dto;

import lombok.Data;

/**
 * 区块里的一笔交易。
 *
 * <p>后面的四个字段是<b>这个页面属于 SmartStorm 而不是通用区块链浏览器</b>的关键：
 * 把区块里的每笔交易与 {@code chain_anchor.tx_hash} 对上，就能标出"这笔是我们房间 5
 * 的存证，覆盖 seq 1–40" —— 否则页面上只是一串串看不懂的哈希。</p>
 */
@Data
public class BlockTxVO {

    /** 交易哈希 */
    private String txHash;

    /** 是否本系统提交的存证交易 */
    private boolean ours;

    /** 以下仅当 ours=true 时有值 */

    /** 存证所属房间 */
    private Long roomId;

    /** 本笔存证覆盖的 seq 区间 */
    private Long fromSeq;
    private Long toSeq;

    /** 本笔存证锚定的 Merkle 根 */
    private String merkleRoot;
}
