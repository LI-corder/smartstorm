package com.smartstorm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * WebSocket 会话查询（骨架版）：供前端调试 / 展示连接数。
 * 真实业务由 WebSocketHandler 直接处理，后续阶段逐步迁移。
 */
@RestController
@RequestMapping("/api/ws")
public class WebSocketController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "endpoint", "ws://localhost:8080/ws",
                "protocol", "自定义 JSON（join_room / add_note / edit_note / ... / op / cursor）",
                "status", "骨架已就绪，第二阶段实现房间会话统计"
        );
    }

    @GetMapping("/room/{roomId}/members")
    public Map<String, Object> roomMembers(@PathVariable String roomId) {
        // 第二阶段：改为从 RoomSessionManager 读取真实在线成员
        return Map.of(
                "roomId", roomId,
                "members", "第二阶段实现：实时在线成员列表",
                "online", 0
        );
    }
}
