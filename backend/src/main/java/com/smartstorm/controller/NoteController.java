package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.common.UnauthorizedException;
import com.smartstorm.dto.NoteRequest;
import com.smartstorm.entity.Note;
import com.smartstorm.entity.Room;
import com.smartstorm.service.NoteService;
import com.smartstorm.service.RoomService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 便利贴 REST 接口（需登录）。
 * 说明：实时协同的便利贴增删改主要走 WebSocket op（同样要求登录）；
 *       REST 保留用于初始化 / 兜底 / 演示。
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final RoomService roomService;

    public NoteController(NoteService noteService, RoomService roomService) {
        this.noteService = noteService;
        this.roomService = roomService;
    }

    @PostMapping
    public Result<Note> create(@RequestBody NoteRequest req) {
        if (req == null || req.getRoomId() == null) {
            return Result.fail(400, "缺少 roomId");
        }
        Room room = roomService.getById(req.getRoomId());
        if (room == null) {
            return Result.fail(404, "房间不存在");
        }
        // 写入者身份以登录用户为准，不信任请求体 userId
        req.setUserId(currentUserId());
        Note note = noteService.create(req);
        return Result.ok(note);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 需登录（Security 已拦截游客），删除操作不校验归属，房间协作场景允许互删
        noteService.delete(id);
        return Result.ok();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedException("未登录或登录已过期");
    }
}
