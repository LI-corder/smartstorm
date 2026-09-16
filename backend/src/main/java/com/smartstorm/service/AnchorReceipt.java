package com.smartstorm.service;

/**
 * 一次上链交易的执行凭据。
 *
 * @param txHash      链上交易哈希
 * @param blockNumber 区块高度；节点未即时返回时为 null
 */
public record AnchorReceipt(String txHash, Long blockNumber) {
}
