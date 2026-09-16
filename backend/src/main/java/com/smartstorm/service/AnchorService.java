package com.smartstorm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartstorm.dto.AnchorVO;
import com.smartstorm.dto.ChainStatusVO;
import com.smartstorm.dto.MerkleProofVO;
import com.smartstorm.entity.ChainAnchor;
import com.smartstorm.entity.OpLog;
import com.smartstorm.handler.RoomSessionManager;
import com.smartstorm.mapper.ChainAnchorMapper;
import com.smartstorm.mapper.OpLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 存证锚定：把一批操作聚成 Merkle 根，锚定到区块链。
 *
 * <p><b>设计原则</b></p>
 * <ol>
 *   <li><b>旁路</b>：锚定由定时任务驱动，绝不进 WebSocket 处理线程 —— 用户拖便利贴的
 *       延迟不受区块链影响。</li>
 *   <li><b>不阻塞</b>：逐房间处理，每个房间独立 try/catch，一个房间失败绝不拖垮其他房间；
 *       失败批次走重试，不与新批次互相牵连。</li>
 *   <li><b>降级</b>：链不可用时照常算 Merkle 根并记成"待上链"，链恢复后自动补发。
 *       本地哈希链校验完全不依赖链。</li>
 * </ol>
 */
@Service
public class AnchorService {

    private static final Logger log = LoggerFactory.getLogger(AnchorService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 未锚定操作累积到这么多条就锚一批 */
    private static final int BATCH_THRESHOLD = 50;

    /** 或者：最早未锚定的操作已超过这么久（秒）就锚一批，保证冷清房间也能及时上链 */
    private static final long MAX_PENDING_SECONDS = 300;

    /** 单批最多覆盖多少条操作，避免一次 Merkle 规模失控 */
    private static final int BATCH_MAX_SIZE = 2000;

    /** 失败重试上限，超过就不再补发（避免对一个坏掉的批次无限重试） */
    private static final int MAX_RETRY = 5;

    private final OpLogMapper opLogMapper;
    private final ChainAnchorMapper anchorMapper;
    private final MerkleService merkleService;
    private final HashChainService hashChainService;
    private final ChainClient chainClient;
    private final RoomSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public AnchorService(OpLogMapper opLogMapper,
                         ChainAnchorMapper anchorMapper,
                         MerkleService merkleService,
                         HashChainService hashChainService,
                         ChainClient chainClient,
                         RoomSessionManager sessionManager,
                         ObjectMapper objectMapper) {
        this.opLogMapper = opLogMapper;
        this.anchorMapper = anchorMapper;
        this.merkleService = merkleService;
        this.hashChainService = hashChainService;
        this.chainClient = chainClient;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    // ---------------- 调度入口 ----------------

    /** 定时任务每轮调用：先补发未完成的批次，再为满足阈值的房间开新批次 */
    public void tick() {
        retryPending();
        for (Long roomId : opLogMapper.listRoomIds()) {
            try {
                anchorRoom(roomId, false);
            } catch (Exception e) {
                // 单房间失败只影响它自己，下一轮还会再来
                log.error("房间 {} 锚定失败（不影响其他房间）", roomId, e);
            }
        }
    }

    /**
     * 为房间锚定一批。
     *
     * @param force true 时跳过阈值判断（手动触发用，答辩演示很方便）
     * @return 新建的锚点；没有可锚的操作或未达阈值时返回 null
     */
    public ChainAnchor anchorRoom(Long roomId, boolean force) {
        // 用「已被批次占用的上界」而不是「已确认的上界」来划起点：
        // 否则一个失败的批次会被反复重建，而 uk_room_range 会一直挡着它。
        // 失败的批次交给 retryPending 补发。
        long from = anchorMapper.getMaxAnchoredSeq(roomId) + 1;
        Long maxSeq = opLogMapper.getMaxSeq(roomId);
        if (maxSeq == null || maxSeq < from) {
            return null;                       // 没有新操作
        }
        long to = Math.min(maxSeq, from + BATCH_MAX_SIZE - 1);

        if (!force && !meetsThreshold(roomId, from, to)) {
            return null;
        }

        // 回填守卫：区间里若还有没算出哈希的行，做出来的 Merkle 根永远对不上
        if (opLogMapper.countUnhashedInRange(roomId, from, to) > 0) {
            log.warn("房间 {} 的区间 [{},{}] 还有未纳入哈希链的操作，本轮跳过锚定", roomId, from, to);
            return null;
        }

        List<OpLog> ops = opLogMapper.listRange(roomId, from, to);
        if (ops.isEmpty()) {
            return null;
        }
        List<String> leaves = ops.stream().map(OpLog::getHash).toList();
        String root = "0x" + merkleService.root(leaves);

        ChainAnchor anchor = new ChainAnchor();
        anchor.setRoomId(roomId);
        anchor.setFromSeq(from);
        anchor.setToSeq(to);
        anchor.setMerkleRoot(root);
        anchor.setStatus(ChainAnchor.STATUS_PENDING);
        anchor.setRetryCount(0);
        try {
            anchorMapper.insert(anchor);
        } catch (DuplicateKeyException e) {
            // uk_room_range 撞了：该区间已被占用（并发或启动补发），交给重试逻辑，不重复建
            log.info("房间 {} 的区间 [{},{}] 已存在锚点记录，跳过", roomId, from, to);
            return null;
        }
        return submit(anchor);
    }

    /**
     * 手动触发一次锚定（跳过阈值判断），供答辩演示用 —— 不必等定时任务。
     *
     * @return 新建的锚点；没有可锚定的新操作时返回 null
     */
    public AnchorVO anchorNow(Long roomId) {
        ChainAnchor anchor = anchorRoom(roomId, true);
        return anchor == null ? null : toVO(anchor);
    }

    // ---------------- 查询 ----------------

    /** 房间存证状态摘要 */
    public ChainStatusVO status(Long roomId) {
        ChainStatusVO vo = new ChainStatusVO();
        vo.setRoomId(roomId);
        vo.setChainEnabled(chainClient.isEnabled());

        Long maxSeq = opLogMapper.getMaxSeq(roomId);
        long max = (maxSeq == null) ? 0L : maxSeq;
        vo.setMaxSeq(max);
        vo.setTotalOps(opLogMapper.countByRoom(roomId));

        Long anchoredSeq = anchorMapper.getMaxAnchoredSeq(roomId);
        long anchored = (anchoredSeq == null) ? 0L : anchoredSeq;
        vo.setAnchoredSeq(anchored);
        vo.setUnanchoredCount(Math.max(0L, max - anchored));

        List<ChainAnchor> anchors = anchorMapper.listByRoom(roomId);
        int confirmed = 0;
        for (ChainAnchor a : anchors) {
            if (a.getStatus() != null && a.getStatus() == ChainAnchor.STATUS_CONFIRMED) {
                confirmed++;
            }
        }
        vo.setAnchorCount(anchors.size());
        vo.setConfirmedCount(confirmed);
        vo.setPendingCount(anchors.size() - confirmed);

        vo.setHeadHash(hashChainService.headHash(roomId));
        return vo;
    }

    /** 房间内的锚点列表，最近的在前 */
    public List<AnchorVO> listAnchors(Long roomId) {
        return anchorMapper.listByRoom(roomId).stream().map(this::toVO).toList();
    }

    /**
     * 某条操作的 Merkle 存在性证明。
     *
     * <p>证明路径不落库 —— op 数据都在 op_log 里且只追加，按同一区间重新聚合即可复现出
     * 完全相同的树。</p>
     *
     * @return null 表示该 seq 还没被任何批次覆盖
     */
    public MerkleProofVO proof(Long roomId, long seq) {
        ChainAnchor anchor = anchorMapper.findBySeq(roomId, seq);
        if (anchor == null) {
            return null;
        }
        List<OpLog> ops = opLogMapper.listRange(roomId, anchor.getFromSeq(), anchor.getToSeq());
        List<String> leaves = ops.stream().map(OpLog::getHash).toList();
        int index = (int) (seq - anchor.getFromSeq());
        if (index < 0 || index >= leaves.size()) {
            return null;
        }

        MerkleProofVO vo = new MerkleProofVO();
        vo.setSeq(seq);
        vo.setLeafHash(leaves.get(index));
        vo.setMerkleRoot(anchor.getMerkleRoot());
        vo.setFromSeq(anchor.getFromSeq());
        vo.setToSeq(anchor.getToSeq());
        vo.setTxHash(anchor.getTxHash());
        vo.setPath(merkleService.proof(leaves, index));
        return vo;
    }

    // ---------------- 内部 ----------------

    /** 是否够一批：条数到阈值，或最早未锚定的操作已经放了太久 */
    private boolean meetsThreshold(Long roomId, long from, long to) {
        if (to - from + 1 >= BATCH_THRESHOLD) {
            return true;
        }
        OpLog oldest = opLogMapper.getBySeq(roomId, from);
        if (oldest == null || oldest.getCreatedAt() == null) {
            return false;
        }
        return oldest.getCreatedAt().plusSeconds(MAX_PENDING_SECONDS).isBefore(LocalDateTime.now());
    }

    /** 补发未上链与失败的批次，跨房间逐个处理，互不影响 */
    private void retryPending() {
        if (!chainClient.isEnabled()) {
            return;
        }
        for (ChainAnchor anchor : anchorMapper.listRetryable(MAX_RETRY)) {
            try {
                submit(anchor);
            } catch (Exception e) {
                log.error("补发批次失败 id={} roomId={}", anchor.getId(), anchor.getRoomId(), e);
            }
        }
    }

    /**
     * 把批次提交上链。
     *
     * <p>链未启用时<b>保持 status=0 直接返回</b>，不标失败 —— 那不是错误，只是还没配好，
     * 链恢复后会被自动补发。</p>
     */
    private ChainAnchor submit(ChainAnchor anchor) {
        if (!chainClient.isEnabled()) {
            log.info("存证链未启用，批次保持待上链 roomId={} seq=[{},{}]",
                    anchor.getRoomId(), anchor.getFromSeq(), anchor.getToSeq());
            return anchor;
        }
        try {
            AnchorReceipt receipt = chainClient.anchor(anchor.getRoomId(), anchor.getMerkleRoot(),
                    anchor.getFromSeq(), anchor.getToSeq());
            anchor.setTxHash(receipt.txHash());
            anchor.setBlockNumber(receipt.blockNumber());
            anchor.setStatus(ChainAnchor.STATUS_CONFIRMED);
            anchor.setConfirmedAt(LocalDateTime.now().withNano(0));
            anchorMapper.updateById(anchor);
            log.info("批次已上链 roomId={} seq=[{},{}] root={} tx={}",
                    anchor.getRoomId(), anchor.getFromSeq(), anchor.getToSeq(),
                    anchor.getMerkleRoot(), receipt.txHash());
            broadcast(anchor);
        } catch (Exception e) {
            anchor.setStatus(ChainAnchor.STATUS_FAILED);
            int retried = (anchor.getRetryCount() == null ? 0 : anchor.getRetryCount()) + 1;
            anchor.setRetryCount(retried);
            anchorMapper.updateById(anchor);
            log.error("批次上链失败（第 {} 次）roomId={} seq=[{},{}]",
                    retried, anchor.getRoomId(), anchor.getFromSeq(), anchor.getToSeq(), e);
        }
        return anchor;
    }

    /** 通知房间内在线成员，让前端存证面板实时更新 */
    private void broadcast(ChainAnchor anchor) {
        try {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("type", "chain_anchor");
            out.set("data", objectMapper.valueToTree(toVO(anchor)));
            sessionManager.broadcastToAll(anchor.getRoomId(), out.toString());
        } catch (Exception e) {
            // 广播失败不该影响锚定结果本身
            log.warn("存证广播失败 roomId={}", anchor.getRoomId(), e);
        }
    }

    private AnchorVO toVO(ChainAnchor a) {
        AnchorVO vo = new AnchorVO();
        vo.setId(a.getId());
        vo.setRoomId(a.getRoomId());
        vo.setFromSeq(a.getFromSeq());
        vo.setToSeq(a.getToSeq());
        vo.setOpCount(a.getFromSeq() == null || a.getToSeq() == null
                ? 0L : a.getToSeq() - a.getFromSeq() + 1);
        vo.setMerkleRoot(a.getMerkleRoot());
        vo.setTxHash(a.getTxHash());
        vo.setBlockNumber(a.getBlockNumber());
        vo.setStatus(a.getStatus());
        vo.setStatusText(statusText(a.getStatus()));
        vo.setCreatedAt(a.getCreatedAt() == null ? "" : a.getCreatedAt().format(FMT));
        vo.setConfirmedAt(a.getConfirmedAt() == null ? "" : a.getConfirmedAt().format(FMT));
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ChainAnchor.STATUS_PENDING -> "待上链";
            case ChainAnchor.STATUS_CONFIRMED -> "已上链";
            case ChainAnchor.STATUS_FAILED -> "上链失败";
            default -> "未知";
        };
    }
}
