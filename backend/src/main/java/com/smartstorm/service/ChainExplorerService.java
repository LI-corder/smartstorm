package com.smartstorm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartstorm.dto.BlockTxVO;
import com.smartstorm.dto.ChainBlockVO;
import com.smartstorm.dto.ChainOverviewVO;
import com.smartstorm.entity.ChainAnchor;
import com.smartstorm.mapper.ChainAnchorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 区块信息查询：把链上的区块与本系统的存证批次关联起来。
 *
 * <p>这是"区块信息页"的灵魂所在。链客户端只会给出一串串交易哈希，而这个服务把它们与
 * {@code chain_anchor.tx_hash} 对上，标出"这笔是房间 5 的存证，覆盖 seq 1–40" ——
 * 否则页面上就只是一堆看不懂的十六进制。</p>
 */
@Service
public class ChainExplorerService {

    private static final Logger log = LoggerFactory.getLogger(ChainExplorerService.class);

    /** 区块列表默认返回条数 */
    public static final int DEFAULT_LIMIT = 20;

    /** 区块列表条数上限。列表是逐个高度查的，每次 n 次 RPC，必须卡住上限 */
    public static final int MAX_LIMIT = 50;

    /** 区块详情里最多展开的交易数（区块本身仍会显示真实交易总数） */
    public static final int MAX_TX_PER_BLOCK = 50;

    private final ChainClient chainClient;
    private final ChainAnchorMapper anchorMapper;

    public ChainExplorerService(ChainClient chainClient, ChainAnchorMapper anchorMapper) {
        this.chainClient = chainClient;
        this.anchorMapper = anchorMapper;
    }

    /**
     * 链概览。
     *
     * <p>本系统的存证统计<b>不依赖链</b>，无论链是否可用都有值；链上字段在链不可用时为
     * null，前端据此显示"—"而不是 0 ——「没连上链」和「链上没有区块」是两回事。</p>
     */
    public ChainOverviewVO overview() {
        ChainOverviewVO vo = chainClient.getOverview();
        if (vo == null) {
            // 链未启用，或者启用了但连不上（区块高度会是 null，前端可区分这两种情况）
            vo = new ChainOverviewVO();
            vo.setChainEnabled(chainClient.isEnabled());
        }
        vo.setAnchorTotal(countAnchors(null));
        vo.setAnchorConfirmed(countAnchors(ChainAnchor.STATUS_CONFIRMED));
        return vo;
    }

    /** 最新区块列表，高度从高到低，并标注其中的本系统存证交易 */
    public List<ChainBlockVO> blocks(int limit) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<ChainBlockVO> blocks = chainClient.listLatestBlocks(n);
        // 列表页要显示"含存证"标记，所以每个区块都要做一次关联查询
        blocks.forEach(this::markOurTransactions);
        return blocks;
    }

    /** 指定高度的区块；不存在或链不可用返回 null。交易过多时只展开前 N 笔。 */
    public ChainBlockVO block(long number) {
        ChainBlockVO block = chainClient.getBlock(number);
        if (block == null) {
            return null;
        }
        markOurTransactions(block);

        List<BlockTxVO> txs = block.getTransactions();
        if (txs != null && txs.size() > MAX_TX_PER_BLOCK) {
            // txCount 保留真实总数，只截断明细列表，让前端能提示"仅显示前 N 笔"
            block.setTransactions(List.copyOf(txs.subList(0, MAX_TX_PER_BLOCK)));
        }
        return block;
    }

    // ---------------- 内部 ----------------

    /**
     * 把区块里的交易与 chain_anchor 对上，标注出属于本系统的存证交易。
     *
     * <p>用 MyBatis-Plus 的 LambdaQueryWrapper 做一次 {@code IN} 查询（项目既有写法），
     * 而不是每笔交易查一次 —— 一个区块可能有几十笔交易。</p>
     */
    private void markOurTransactions(ChainBlockVO block) {
        List<BlockTxVO> txs = block.getTransactions();
        if (txs == null || txs.isEmpty()) {
            return;
        }
        List<String> hashes = txs.stream()
                .map(BlockTxVO::getTxHash)
                .filter(Objects::nonNull)
                .toList();
        if (hashes.isEmpty()) {
            return;
        }

        Map<String, ChainAnchor> ourAnchors;
        try {
            ourAnchors = anchorMapper.selectList(new LambdaQueryWrapper<ChainAnchor>()
                            .in(ChainAnchor::getTxHash, hashes))
                    .stream()
                    .filter(a -> a.getTxHash() != null)
                    // tx_hash 有唯一性，重复只可能是数据异常，保留第一条即可
                    .collect(Collectors.toMap(ChainAnchor::getTxHash, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            // 关联失败不该让整个区块页挂掉，退化成"不加标注"即可
            log.warn("区块 {} 的存证关联查询失败：{}", block.getNumber(), e.getMessage());
            return;
        }

        for (BlockTxVO tx : txs) {
            ChainAnchor anchor = ourAnchors.get(tx.getTxHash());
            if (anchor != null) {
                tx.setOurs(true);
                tx.setRoomId(anchor.getRoomId());
                tx.setFromSeq(anchor.getFromSeq());
                tx.setToSeq(anchor.getToSeq());
                tx.setMerkleRoot(anchor.getMerkleRoot());
            }
        }
    }

    /** status 传 null 表示统计全部 */
    private int countAnchors(Integer status) {
        LambdaQueryWrapper<ChainAnchor> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ChainAnchor::getStatus, status);
        }
        Long count = anchorMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }
}
