package com.smartstorm.fisco;

import com.smartstorm.dto.BlockTxVO;
import com.smartstorm.dto.ChainBlockVO;
import com.smartstorm.dto.ChainOverviewVO;
import com.smartstorm.service.AnchorReceipt;
import com.smartstorm.service.ChainClient;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlockHeader;
import org.fisco.bcos.sdk.v3.client.protocol.response.TotalTransactionCount;
import org.fisco.bcos.sdk.v3.codec.ContractCodecException;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * FISCO BCOS 实现：调用 {@code SmartStormAnchor} 合约上链 + 查询区块。
 *
 * <p><b>本类是整个工程里唯一出现 FISCO SDK 类型的地方</b>（连同 {@link FiscoConfig}）。
 * 所有调用方只依赖 {@code ChainClient} 接口 —— 因为这个 SDK 底层是 JNI 封装的 C SDK，
 * 依赖平台相关的原生库，一旦被加载而平台上没有对应原生库，应用会直接启动失败。
 * 藏在接口后面，链不可用时应用照常跑。</p>
 *
 * <p><b>职责边界</b>：本类只负责"跟链说话"，产出的 {@code BlockTxVO} 里只有交易哈希，
 * {@code ours} / 房间号 / seq 区间这些需要查数据库的字段由 {@code ChainExplorerService}
 * 填充。链客户端不该依赖本项目的数据库。</p>
 *
 * <p>只上 Merkle 根，便利贴明文绝不上链。</p>
 */
public class FiscoChainClient implements ChainClient {

    private static final Logger log = LoggerFactory.getLogger(FiscoChainClient.class);

    /** 合约调用成功的返回码 */
    private static final int RETURN_CODE_SUCCESS = 0;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BcosSDK sdk;
    private final Client client;
    private final AssembleTransactionProcessor processor;
    private final String contractAddress;
    private final String abi;

    public FiscoChainClient(String configFile, String contractAddress,
                            String group, String abiLocation) {
        this.contractAddress = contractAddress;
        this.abi = readClasspathText(abiLocation);
        this.sdk = BcosSDK.build(configFile);
        this.client = sdk.getClient(group);
        CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
        this.processor = createProcessor(client, keyPair);
        log.info("FISCO 存证客户端已初始化 group={} contract={}", group, contractAddress);
    }

    /**
     * 构建合约交易处理器。
     *
     * <p>SDK 在这里声明了受检的 {@code IOException}，但接口层不该染上它 ——
     * 包成运行时异常，由 {@code AnchorService} 统一按"上链失败"处理并重试。</p>
     *
     * <p>后两个参数是 abi/ 与 bin/ 的资源目录，仅供 SDK 按合约名装载用；本类实际调用时
     * 直接传 abi 字符串，所以传固定目录名即可。</p>
     */
    private static AssembleTransactionProcessor createProcessor(Client client, CryptoKeyPair keyPair) {
        try {
            return TransactionProcessorFactory
                    .createAssembleTransactionProcessor(client, keyPair, "abi/", "");
        } catch (IOException e) {
            throw new IllegalStateException("构建合约交易处理器失败（abi 资源目录不可用）", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ---------------- 锚定 ----------------

    @Override
    public AnchorReceipt anchor(long roomId, String merkleRoot, long fromSeq, long toSeq) {
        List<Object> params = List.of(
                BigInteger.valueOf(roomId),
                merkleRoot,
                BigInteger.valueOf(fromSeq),
                BigInteger.valueOf(toSeq));

        TransactionResponse response;
        try {
            response = processor.sendTransactionAndGetResponse(
                    contractAddress, abi, "anchor", params);
        } catch (ContractCodecException e) {
            throw new IllegalStateException(
                    "合约参数 ABI 编码失败，请确认 abi 文件与合约版本一致：merkleRoot=" + merkleRoot, e);
        }

        if (response.getReturnCode() != RETURN_CODE_SUCCESS) {
            throw new IllegalStateException("合约 anchor 调用失败：returnCode="
                    + response.getReturnCode() + "，returnMessage=" + response.getReturnMessage());
        }

        TransactionReceipt receipt = response.getTransactionReceipt();
        if (receipt == null) {
            throw new IllegalStateException("合约 anchor 调用未返回交易回执");
        }
        // 回执状态非 0 说明交易上链了但执行失败（例如参数编码错误、合约内部 revert）
        if (receipt.getStatus() != RETURN_CODE_SUCCESS) {
            throw new IllegalStateException("交易回执状态异常：status=" + receipt.getStatus()
                    + "，message=" + receipt.getMessage());
        }

        // SDK 的链上数值统一是 BigInteger；区块高度用 long 足够，不会溢出
        BigInteger rawBlockNumber = receipt.getBlockNumber();
        Long blockNumber = (rawBlockNumber == null) ? null : rawBlockNumber.longValue();
        return new AnchorReceipt(receipt.getTransactionHash(), blockNumber);
    }

    // ---------------- 区块查询 ----------------

    @Override
    public Long getBlockNumber() {
        try {
            return client.getBlockNumber().getBlockNumber().longValue();
        } catch (Exception e) {
            // 读接口一律不抛：链抖动时页面显示不了区块，但不该把请求打成 500
            log.warn("查询区块高度失败：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public ChainBlockVO getBlock(long number) {
        try {
            BcosBlock response = client.getBlockByNumber(BigInteger.valueOf(number), true, false);
            return toVO(response);
        } catch (Exception e) {
            log.warn("查询区块 {} 失败：{}", number, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ChainBlockVO> listLatestBlocks(int limit) {
        Long head = getBlockNumber();
        if (head == null) {
            return List.of();
        }
        // FISCO 没有"取最近 N 个区块"的接口，只能按高度逐个查，所以这是 limit 次 RPC。
        // 必须取完整区块（includeTransactions=true）—— 列表要显示"含存证"标记，
        // 就得拿到区块里的交易哈希去和 chain_anchor 比对。
        List<ChainBlockVO> out = new ArrayList<>(limit);
        for (long n = head; n >= 0 && out.size() < limit; n--) {
            ChainBlockVO block = getBlock(n);
            if (block != null) {
                out.add(block);
            }
        }
        return out;
    }

    @Override
    public ChainOverviewVO getOverview() {
        try {
            ChainOverviewVO vo = new ChainOverviewVO();
            vo.setChainEnabled(true);

            TotalTransactionCount response = client.getTotalTransactionCount();
            if (response != null && response.getTotalTransactionCount() != null) {
                TotalTransactionCount.TransactionCountInfo info = response.getTotalTransactionCount();
                vo.setTxCount(parseChainNumber(info.getTransactionCount()));
                vo.setBlockNumber(parseChainNumber(info.getBlockNumber()));
                vo.setFailedTxCount(parseChainNumber(info.getFailedTransactionCount()));
            }

            // 共识节点数只能从区块头拿：多取一次最新区块，但只取头不取交易，开销很小
            Long head = getBlockNumber();
            if (head != null && vo.getBlockNumber() == null) {
                vo.setBlockNumber(head);
            }
            if (head != null) {
                vo.setSealerCount(getSealerCount(head));
            }
            return vo;
        } catch (Exception e) {
            log.warn("查询链概览失败：{}", e.getMessage());
            return null;
        }
    }

    // ---------------- 内部 ----------------

    private Integer getSealerCount(long blockNumber) {
        try {
            BcosBlock response = client.getBlockByNumber(BigInteger.valueOf(blockNumber), false, true);
            if (response == null || response.getBlock() == null
                    || response.getBlock().getSealerList() == null) {
                return null;
            }
            return response.getBlock().getSealerList().size();
        } catch (Exception e) {
            log.debug("查询共识节点数失败：{}", e.getMessage());
            return null;
        }
    }

    private ChainBlockVO toVO(BcosBlock response) {
        if (response == null || response.getBlock() == null) {
            return null;
        }
        BcosBlock.Block block = response.getBlock();

        ChainBlockVO vo = new ChainBlockVO();
        vo.setNumber(block.getNumber());
        vo.setHash(block.getHash());
        vo.setTimestampSeconds(block.getTimestamp());
        vo.setTimestamp(formatTime(block.getTimestamp()));
        vo.setStateRoot(block.getStateRoot());
        vo.setTransactionsRoot(block.getTransactionsRoot());
        vo.setVersion(block.getVersion());
        vo.setGasUsed(block.getGasUsed());
        vo.setSealerIndex(block.getSealer());
        vo.setSealerCount(block.getSealerList() == null ? null : block.getSealerList().size());

        // 父区块哈希：区块头里给的是 ParentInfo 列表
        // 注意 ParentInfo 与 BlockHeader 都是 BcosBlockHeader 的直接嵌套类，不是 Block 的
        List<BcosBlockHeader.ParentInfo> parents = block.getParentInfo();
        if (parents != null && !parents.isEmpty() && parents.get(0) != null) {
            vo.setParentHash(parents.get(0).getBlockHash());
        }

        List<BlockTxVO> txs = new ArrayList<>();
        if (block.getTransactionHashes() != null) {
            for (BcosBlock.TransactionHash t : block.getTransactionHashes()) {
                if (t == null || t.get() == null) {
                    continue;
                }
                BlockTxVO tx = new BlockTxVO();
                tx.setTxHash(t.get());
                // ours / 房间号 / seq 区间由 ChainExplorerService 查库后填充
                txs.add(tx);
            }
        }
        vo.setTxCount(txs.size());
        vo.setTransactions(txs);
        return vo;
    }

    private static String formatTime(long epochSeconds) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSeconds), ZONE).format(FMT);
    }

    /**
     * 链上数值字段（如 {@code getTotalTransactionCount()} 的三个返回值）给的是字符串，
     * 可能是 0x 前缀的十六进制，也可能是十进制 —— 不能直接 {@code Long.parseLong}。
     */
    private static Long parseChainNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String s = raw.trim();
            return (s.startsWith("0x") || s.startsWith("0X"))
                    ? Long.parseLong(s.substring(2), 16)
                    : Long.parseLong(s);
        } catch (NumberFormatException e) {
            log.debug("无法解析链上数值：{}", raw);
            return null;
        }
    }

    /** 读 classpath 下的文本资源（合约 ABI） */
    private static String readClasspathText(String location) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取合约 ABI 失败：" + location
                    + "（应由控制台的 sol2java.sh 生成后放入 src/main/resources/abi/）", e);
        }
    }
}
