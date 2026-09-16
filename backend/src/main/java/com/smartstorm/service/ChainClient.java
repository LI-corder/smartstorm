package com.smartstorm.service;

import com.smartstorm.dto.ChainBlockVO;
import com.smartstorm.dto.ChainOverviewVO;

import java.util.List;

/**
 * 存证链接口：锚定写入 + 区块查询。
 *
 * <p><b>这个接口里绝不能出现任何具体区块链 SDK 的类型。</b>FISCO BCOS 的 Java SDK
 * 底层是 JNI 封装的 C SDK，依赖平台相关的原生库；一旦某个类的签名里出现了 SDK 类型，
 * Spring 加载配置类时就会解析到它，进而可能触发原生库加载。把实现藏在接口后面，
 * 链不可用时应用照常启动。</p>
 *
 * <p>读接口（区块查询）与写接口（锚定）放在一起，是因为它们访问同一个 SDK 客户端、
 * 同一个条件化配置、同一个降级开关 —— 拆成两个接口只会让 no-op 实现和配置类翻倍。</p>
 *
 * <p>实现有两个：{@code NoopChainClient}（链未启用，默认）与 {@code FiscoChainClient}
 * （条件化装配）。<b>所有读方法在链不可用时返回 null 或空列表，绝不抛异常</b> ——
 * 调用方据此展示降级态。</p>
 */
public interface ChainClient {

    /** 链是否可用。false 时所有读方法返回空值，{@link #anchor} 不应被调用。 */
    boolean isEnabled();

    // ---------------- 锚定（写） ----------------

    /**
     * 把一个 Merkle 根锚定上链。
     *
     * @param merkleRoot 0x 前缀的 Merkle 根
     * @return 交易凭据
     * @throws RuntimeException 上链失败（网络不通、节点未起、合约调用回执失败等）
     */
    AnchorReceipt anchor(long roomId, String merkleRoot, long fromSeq, long toSeq);

    // ---------------- 区块查询（读） ----------------

    /** 当前区块高度；链不可用返回 null */
    Long getBlockNumber();

    /** 指定高度的区块；不存在或链不可用返回 null */
    ChainBlockVO getBlock(long number);

    /**
     * 最近 n 个区块，高度从高到低。
     *
     * <p>FISCO BCOS 没有"取最近 N 个区块"的接口，只能按高度逐个查，因此这是 n 次 RPC。
     * 本地链上 n=20 是亚秒级的，调用方需自行限制 n 的上限。</p>
     */
    List<ChainBlockVO> listLatestBlocks(int limit);

    /** 链级统计；链不可用返回 null */
    ChainOverviewVO getOverview();
}
