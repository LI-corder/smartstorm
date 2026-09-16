package com.smartstorm.service;

import com.smartstorm.dto.ChainBlockVO;
import com.smartstorm.dto.ChainOverviewVO;

import java.util.List;

/**
 * 存证链未启用时的空实现，是默认装配。
 *
 * <p>它保证应用在没有区块链环境时照常启动：{@code op_log} 的哈希链照常工作、Merkle 根
 * 照常计算并记成"待上链"，只是没有交易哈希。等链可用了，这些待上链的批次会被自动补发。</p>
 *
 * <p>读方法一律返回 null / 空列表 —— 区块信息页据此渲染降级态，而不是报错或空白。
 * 但 {@link #anchor} 是直接抛异常而不是返回 null：调用方必须先看 {@link #isEnabled()}，
 * 真走到这里说明逻辑有误，不该静默成功。</p>
 */
public class NoopChainClient implements ChainClient {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AnchorReceipt anchor(long roomId, String merkleRoot, long fromSeq, long toSeq) {
        throw new IllegalStateException(
                "存证链未启用，不应调用 anchor；请先检查 app.fisco.enabled");
    }

    @Override
    public Long getBlockNumber() {
        return null;
    }

    @Override
    public ChainBlockVO getBlock(long number) {
        return null;
    }

    @Override
    public List<ChainBlockVO> listLatestBlocks(int limit) {
        return List.of();
    }

    @Override
    public ChainOverviewVO getOverview() {
        return null;
    }
}
