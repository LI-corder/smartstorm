package com.smartstorm.runner;

import com.smartstorm.entity.OpLog;
import com.smartstorm.mapper.OpLogMapper;
import com.smartstorm.service.HashChainService;
import com.smartstorm.service.OpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量操作日志的哈希链回填。
 *
 * <p>哈希链是后加的，之前落库的 op_log 行没有 {@code prev_hash} / {@code hash}。
 * 启动时按房间、按 seq 升序补算，让整段历史都能被 {@code verify} 校验。</p>
 *
 * <p><b>为什么必须持房间写锁</b>：回填期间用户可能正在画布上操作。若不锁，新操作会
 * 取到"还没回填的上一行"当链头（那一行的 hash 还是 null），凭空造出一条断链 ——
 * 而且这种断链事后无法修复，因为新操作的哈希已经按下错的链头算死了。</p>
 *
 * <p><b>可重入</b>：只处理 {@code hash IS NULL} 的行，且按 seq 升序 —— 中途失败重跑会
 * 从断点继续，不会把已算好的行重算错。</p>
 */
@Component
public class OpLogHashBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpLogHashBackfillRunner.class);

    /** 进度日志的输出间隔（行） */
    private static final int PROGRESS_STEP = 500;

    private final OpLogMapper opLogMapper;
    private final HashChainService hashChainService;
    private final OpService opService;

    public OpLogHashBackfillRunner(OpLogMapper opLogMapper,
                                   HashChainService hashChainService,
                                   OpService opService) {
        this.opLogMapper = opLogMapper;
        this.hashChainService = hashChainService;
        this.opService = opService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> roomIds = opLogMapper.listRoomsWithNullHash();
        if (roomIds.isEmpty()) {
            log.info("存量操作日志均已纳入哈希链，无需回填");
            return;
        }
        log.info("发现 {} 个房间存在未纳入哈希链的操作，开始回填", roomIds.size());

        int total = 0;
        int failed = 0;
        for (Long roomId : roomIds) {
            try {
                total += backfillRoom(roomId);
            } catch (Exception e) {
                // 单个房间失败不影响其他房间；该房间的链会不完整，verify 能报出来
                failed++;
                log.error("房间 {} 哈希回填失败，该房间的链可能不完整", roomId, e);
            }
        }
        log.info("存量回填结束：成功 {} 条，失败 {} 个房间", total, failed);
    }

    /** 回填单个房间，全程持该房间的写锁 */
    private int backfillRoom(Long roomId) {
        return opService.runExclusive(roomId, () -> {
            List<OpLog> pending = opLogMapper.listNullHashByRoom(roomId);
            if (pending.isEmpty()) {
                return 0;
            }

            OpLog first = pending.get(0);
            String prevHash = (first.getSeq() != null && first.getSeq() == 1L)
                    ? HashChainService.GENESIS
                    : predecessorHash(roomId, first.getSeq() - 1);

            int done = 0;
            for (OpLog op : pending) {
                String hash = hashChainService.computeHash(prevHash, op.getRoomId(), op.getSeq(),
                        op.getUserId(), op.getType(), op.getPayload(), op.getCreatedAt());
                op.setPrevHash(prevHash);
                op.setHash(hash);
                opLogMapper.updateById(op);
                prevHash = hash;

                done++;
                if (done % PROGRESS_STEP == 0) {
                    log.info("房间 {} 哈希回填进度：{}/{}", roomId, done, pending.size());
                }
            }
            log.info("房间 {} 哈希回填完成，共 {} 条", roomId, done);
            return done;
        });
    }

    /** 取 seq-1 那条的哈希作为链头；缺失说明数据有空洞，直接报错而不是硬造一条链 */
    private String predecessorHash(Long roomId, long seq) {
        OpLog prev = opLogMapper.getBySeq(roomId, seq);
        if (prev == null || prev.getHash() == null) {
            throw new IllegalStateException(
                    "房间 " + roomId + " 中 seq=" + seq + " 的前驱记录缺失或未纳入哈希链，无法续接");
        }
        return prev.getHash();
    }
}
