package com.smartstorm.controller;

import com.smartstorm.common.Result;
import com.smartstorm.dto.MemberJoinRequest;
import com.smartstorm.dto.NoteVO;
import com.smartstorm.dto.RoomCreateRequest;
import com.smartstorm.entity.Member;
import com.smartstorm.entity.Note;
import com.smartstorm.entity.Room;
import com.smartstorm.service.NoteService;
import com.smartstorm.service.RoomService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 房间相关 REST 接口。
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final NoteService noteService;

    public RoomController(RoomService roomService, NoteService noteService) {
        this.roomService = roomService;
        this.noteService = noteService;
    }

    /** 创建房间（需登录），ownerId 取当前登录用户 */
    @PostMapping
    public Result<Room> create(@RequestBody RoomCreateRequest req) {
        Room room = roomService.createRoom(req == null ? null : req.getName(), currentUserId());
        return Result.ok(room);
    }

    /** 按房间号查询房间 */
    @GetMapping("/code/{roomCode}")
    public Result<Room> getByCode(@PathVariable String roomCode) {
        Room room = roomService.getByCode(roomCode);
        if (room == null) {
            return Result.fail(404, "房间不存在");
        }
        return Result.ok(room);
    }

    /** 按 id 查询房间 */
    @GetMapping("/{roomId}")
    public Result<Room> getById(@PathVariable Long roomId) {
        Room room = roomService.getById(roomId);
        if (room == null) {
            return Result.fail(404, "房间不存在");
        }
        return Result.ok(room);
    }

    /** 加入房间（幂等），返回成员信息 */
    @PostMapping("/{roomId}/join")
    public Result<Member> join(@PathVariable Long roomId, @RequestBody MemberJoinRequest req) {
        if (!roomService.exists(roomId)) {
            return Result.fail(404, "房间不存在");
        }
        if (req == null || req.getUserId() == null) {
            return Result.fail(400, "缺少 userId");
        }
        Member member = roomService.joinRoom(roomId, req.getUserId(), req.getUserName());
        return Result.ok(member);
    }

    /** 房间成员列表 */
    @GetMapping("/{roomId}/members")
    public Result<List<Member>> members(@PathVariable Long roomId) {
        return Result.ok(roomService.listMembers(roomId));
    }

    /** 房间内所有便利贴（当前快照） */
    @GetMapping("/{roomId}/notes")
    public Result<List<NoteVO>> notes(@PathVariable Long roomId) {
        List<Note> notes = noteService.listByRoom(roomId);
        return Result.ok(noteService.toVO(notes));
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
