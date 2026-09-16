package com.smartstorm.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间会话管理器。
 *
 * <p>维护 房间 → 会话 的映射，以及 会话 → 用户信息 的映射。
 * 提供加入/离开、在线用户列表、按房间广播能力。</p>
 */
@Component
public class RoomSessionManager {

    private static final Logger log = LoggerFactory.getLogger(RoomSessionManager.class);

    /**
     * 房间内在线用户信息（loggedIn 标记该会话是否已登录，游客只读）。
     *
     * <p>{@code avatarUrl} 为自定义头像地址，未上传时是<b>空串而非 null</b> ——
     * 它会被整体序列化进 {@code user_list} 广播，而下面的 op/cursor 消息还要把它塞进
     * {@code Map.of(...)}，那个方法不接受 null 值。统一用空串省掉一处踩坑点。</p>
     */
    public record SessionUser(String userId, String userName, String color,
                              String avatarUrl, boolean loggedIn) {
    }

    /** roomId -> sessionId -> session */
    private final Map<Long, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();

    /** sessionId -> roomId */
    private final Map<String, Long> sessionRoom = new ConcurrentHashMap<>();

    /** sessionId -> 用户信息 */
    private final Map<String, SessionUser> sessionUser = new ConcurrentHashMap<>();

    public void join(Long roomId, WebSocketSession session, String userId, String userName,
                     String color, String avatarUrl, boolean loggedIn) {
        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        sessionRoom.put(session.getId(), roomId);
        sessionUser.put(session.getId(),
                new SessionUser(userId, userName, color, avatarUrl == null ? "" : avatarUrl, loggedIn));
    }

    public void leave(WebSocketSession session) {
        Long roomId = sessionRoom.remove(session.getId());
        if (roomId != null) {
            Map<String, WebSocketSession> members = rooms.get(roomId);
            if (members != null) {
                members.remove(session.getId());
                if (members.isEmpty()) {
                    rooms.remove(roomId);
                }
            }
        }
        sessionUser.remove(session.getId());
    }

    public SessionUser getUser(String sessionId) {
        return sessionUser.get(sessionId);
    }

    /** 会话是否已登录（游客只读，登录用户可写） */
    public boolean isLoggedIn(String sessionId) {
        SessionUser u = sessionUser.get(sessionId);
        return u != null && u.loggedIn();
    }

    public Long getRoomId(String sessionId) {
        return sessionRoom.get(sessionId);
    }

    /** 房间内当前在线用户列表 */
    public List<SessionUser> onlineUsers(Long roomId) {
        List<SessionUser> list = new ArrayList<>();
        Map<String, WebSocketSession> members = rooms.get(roomId);
        if (members == null) {
            return list;
        }
        for (String sessionId : members.keySet()) {
            SessionUser u = sessionUser.get(sessionId);
            if (u != null) {
                list.add(u);
            }
        }
        return list;
    }

    /** 广播给房间内除发件人外的所有人 */
    public void broadcast(Long roomId, String exceptSessionId, String payload) {
        Map<String, WebSocketSession> members = rooms.get(roomId);
        if (members == null) {
            return;
        }
        for (Map.Entry<String, WebSocketSession> e : members.entrySet()) {
            if (e.getKey().equals(exceptSessionId)) {
                continue;
            }
            send(e.getValue(), payload);
        }
    }

    /** 广播给房间内所有人（含发件人，用于 op 回执） */
    public void broadcastToAll(Long roomId, String payload) {
        Map<String, WebSocketSession> members = rooms.get(roomId);
        if (members == null) {
            return;
        }
        for (WebSocketSession s : members.values()) {
            send(s, payload);
        }
    }

    public void send(WebSocketSession session, String payload) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.warn("发送消息失败 session={}, error={}", session.getId(), e.getMessage());
            }
        }
    }
}
