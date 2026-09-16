package com.smartstorm.task;

import com.smartstorm.service.AnchorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 存证锚定的定时触发。
 *
 * <p>单独放一个 task 包，让 {@code AnchorService} 保持纯业务、不掺调度细节。</p>
 *
 * <p>任务本身只做一件事：调 service 并把所有异常挡在这里。定时任务里漏出去的异常虽然
 * 不会终止后续调度，但会在日志里留下无上下文的堆栈，不如自己记一笔。</p>
 */
@Component
public class ChainAnchorTask {

    private static final Logger log = LoggerFactory.getLogger(ChainAnchorTask.class);

    private final AnchorService anchorService;

    public ChainAnchorTask(AnchorService anchorService) {
        this.anchorService = anchorService;
    }

    /**
     * 每 60 秒扫一轮。
     *
     * <p>首次延迟 30 秒 —— 存量哈希回填是 ApplicationRunner，在容器刷新后执行，
     * 给回填留出完成时间，避免锚定先行拿到半截数据。</p>
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void tick() {
        try {
            anchorService.tick();
        } catch (Exception e) {
            log.error("存证锚定任务异常", e);
        }
    }
}
