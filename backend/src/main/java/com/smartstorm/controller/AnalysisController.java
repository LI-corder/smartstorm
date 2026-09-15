package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.common.UnauthorizedException;
import com.smartstorm.dto.AnalysisVO;
import com.smartstorm.service.AnalysisService;
import com.smartstorm.service.RoomService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 智能整理接口（第三阶段）。
 * 触发分析需要登录；读取最近一次结果匿名可访问（供刷新/晚加入恢复覆盖层）。
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/analysis")
public class AnalysisController {

    private final RoomService roomService;
    private final AnalysisService analysisService;

    public AnalysisController(RoomService roomService, AnalysisService analysisService) {
        this.roomService = roomService;
        this.analysisService = analysisService;
    }

    /** 触发一次智能整理（分组 + 冲突），结果落库并广播给房间所有在线成员 */
    @PostMapping
    public Result<AnalysisVO> analyze(@PathVariable Long roomId) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        Long userId = currentUserId();
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        return Result.ok(analysisService.analyze(roomId, userId));
    }

    /** 最近一次智能整理结果（无则 data 为 null） */
    @GetMapping("/latest")
    public Result<AnalysisVO> latest(@PathVariable Long roomId) {
        return Result.ok(analysisService.latest(roomId));
    }

    /** 当前登录用户 id（由 JWT 写入 SecurityContext；未登录为 null） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
