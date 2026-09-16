package com.smartstorm.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartstorm.config.WebSocketConfig;
import com.smartstorm.dto.NoteVO;
import com.smartstorm.entity.Member;
import com.smartstorm.entity.Note;
import com.smartstorm.entity.Room;
import com.smartstorm.entity.User;
import com.smartstorm.service.AuthService;
import com.smartstorm.service.NoteService;
import com.smartstorm.service.OpService;
import com.smartstorm.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;

/**
 * 实时协同 WebSocket 处理器（协议分发中心）。
 *
 * <p>协议：
 * <ul>
 *   <li>入站：join_room / add_note / edit_note / move_note / color_note / delete_note / cursor_move / leave_room</li>
 *   <li>出站：room_state（快照）/ user_list / op（操作广播+回执）/ cursor / error</li>
 * </ul>
 *
 * <p>一致性模型：服务器为每个操作分配房间内递增 seq 并落库（同时串上链式哈希），
 * 再将富化后的 op 广播给房间内所有人（含发件人，作为持久化回执）。
 * 所有人按 seq 顺序应用 → 状态一致。cursor 只转发不落库。</p>
 */
@Component
public class CustomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final RoomSessionManager sessionManager;
    private final RoomService roomService;
    private final NoteService noteService;
    private final OpService opService;
    private final AuthService authService;

    public CustomWebSocketHandler(ObjectMapper objectMapper,
                                  RoomSessionManager sessionManager,
                                  RoomService roomService,
                                  NoteService noteService,
                                  OpService opService,
                                  AuthService authService) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.roomService = roomService;
        this.noteService = noteService;
        this.opService = opService;
        this.authService = authService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");

            switch (type) {
                case "join_room" -> handleJoin(session, root);
                case "leave_room" -> {
                    leave(session);
                }
                case "cursor_move" -> handleCursor(session, root);
                case "add_note", "edit_note", "move_note", "color_note", "delete_note" ->
                        handleOp(session, root, type);
                default -> {
                    log.warn("未知消息类型: {}", type);
                    send(session, error("未知消息类型: " + type));
                }
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
            send(session, error("消息处理失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
        }
    }

    // ---------------- 入站处理 ----------------

    private void handleJoin(WebSocketSession session, JsonNode root) {
        long roomId = root.path("roomId").asLong();
        JsonNode data = root.path("data");

        // 登录态与身份：由握手时的 JWT 确定，不信任客户端传值
        Object userIdAttr = session.getAttributes().get(WebSocketConfig.ATTR_USER_ID);
        boolean loggedIn = userIdAttr != null;

        String userId;
        String userName;
        String color;
        // 头像统一用空串表示「没有」，不用 null —— 它后面要塞进 Map.of(...) 广播，那个不接受 null
        String avatarUrl;
        if (loggedIn) {
            Long uid = (Long) userIdAttr;
            User user = authService.getById(uid);
            if (user == null) {
                send(session, error("用户不存在"));
                return;
            }
            userId = String.valueOf(user.getId());
            userName = user.getNickname();
            color = user.getAvatarColor();
            avatarUrl = user.getAvatarUrl() == null ? "" : user.getAvatarUrl();
        } else {
            userId = data.path("userId").asText(session.getId());
            userName = data.path("userName").asText("访客");
            color = data.path("color").asText("blue");
            avatarUrl = "";
        }

        if (roomId <= 0) {
            send(session, error("缺少 roomId"));
            return;
        }
        Room room = roomService.getById(roomId);
        if (room == null) {
            send(session, error("房间不存在"));
            return;
        }

        // 登记会话 + 在 member 表登记（幂等），供快照关联操作者信息
        sessionManager.join(roomId, session, userId, userName, color, avatarUrl, loggedIn);
        roomService.joinRoom(roomId, parseUserId(userId), userName);
        log.info("用户加入房间 roomId={}, user={} ({}), loggedIn={}", roomId, userName, userId, loggedIn);

        // 1. 下发全量快照（当前便利贴）
        List<Note> notes = noteService.listByRoom(roomId);
        List<NoteVO> voList = noteService.toVO(notes);
        ObjectNode state = objectMapper.createObjectNode();
        state.put("type", "room_state");
        state.set("data", objectMapper.valueToTree(Map.of("notes", voList)));
        send(session, state.toString());

        // 2. 广播最新在线用户列表
        broadcastUserList(roomId);
    }

    private void handleOp(WebSocketSession session, JsonNode root, String type) {
        long roomId = root.path("roomId").asLong();
        JsonNode data = root.path("data");
        RoomSessionManager.SessionUser user = sessionManager.getUser(session.getId());
        if (user == null) {
            send(session, error("请先加入房间"));
            return;
        }
        // 写权限：游客只读
        if (!user.loggedIn()) {
            send(session, error("游客只读，请先登录后再操作"));
            return;
        }
        Long actualRoom = sessionManager.getRoomId(session.getId());
        if (actualRoom == null || actualRoom != roomId) {
            send(session, error("房间不匹配"));
            return;
        }

        // 1+2. 更新 note 当前状态 + 记录操作日志（含链式哈希），两步在同一把房间写锁内
        //      完成。分开做会在并发下出现"便利贴状态改了但没记日志"，画布与哈希链分叉。
        //      写入者身份用握手时 JWT 解析出的 userId，不信任客户端传值。
        Long writerId = (Long) session.getAttributes().get(WebSocketConfig.ATTR_USER_ID);
        OpService.OpResult result = opService.applyAndRecord(roomId, writerId, type, data);
        if (result == null) {
            send(session, error("操作无效: " + type));
            return;
        }
        ObjectNode enriched = result.payload();
        var op = result.op();
        log.info("op roomId={} seq={} type={} user={}", roomId, op.getSeq(), type, user.userName());

        // 3. 广播给房间内所有人（含发件人回执），带 seq 与用户信息
        ObjectNode out = objectMapper.createObjectNode();
        out.put("type", "op");
        out.put("seq", op.getSeq());
        out.set("user", objectMapper.valueToTree(Map.of(
                "id", user.userId(),
                "name", user.userName(),
                "color", user.color(),
                "avatarUrl", user.avatarUrl())));
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("type", type);
        dataNode.set("payload", enriched);
        out.set("data", dataNode);
        sessionManager.broadcastToAll(roomId, out.toString());
    }

    private void handleCursor(WebSocketSession session, JsonNode root) {
        long roomId = root.path("roomId").asLong();
        JsonNode data = root.path("data");
        RoomSessionManager.SessionUser user = sessionManager.getUser(session.getId());
        if (user == null) {
            return;
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("type", "cursor");
        out.set("user", objectMapper.valueToTree(Map.of(
                "id", user.userId(),
                "name", user.userName(),
                "color", user.color(),
                "avatarUrl", user.avatarUrl())));
        out.set("data", data);
        // 光标只转发给其他人，不落库
        sessionManager.broadcast(roomId, session.getId(), out.toString());
    }

    private void leave(WebSocketSession session) {
        Long roomId = sessionManager.getRoomId(session.getId());
        sessionManager.leave(session);
        log.info("用户离开房间 roomId={}", roomId);
        if (roomId != null) {
            broadcastUserList(roomId);
        }
    }

    // ---------------- 出站 ----------------

    private void broadcastUserList(Long roomId) {
        List<RoomSessionManager.SessionUser> users = sessionManager.onlineUsers(roomId);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("type", "user_list");
        out.set("data", objectMapper.valueToTree(Map.of("users", users)));
        sessionManager.broadcastToAll(roomId, out.toString());
    }

    private String error(String message) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("type", "error");
        out.put("message", message);
        return out.toString();
    }

    private long parseUserId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void send(WebSocketSession session, String payload) {
        sessionManager.send(session, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leave(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常 session={}, error={}", session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
        }
    }
}
