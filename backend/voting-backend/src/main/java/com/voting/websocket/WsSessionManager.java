package com.voting.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 在线用户管理 + 消息广播
 * 按 userId 去重: 同一用户多次连接/刷新/重连只保留最新会话
 */
@Slf4j
@Component
public class WsSessionManager {

    private final ObjectMapper objectMapper;

    public WsSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** sessionId -> 用户信息 */
    private final Map<String, Map<String, Object>> sessionUsers = new ConcurrentHashMap<>();

    /** userId -> 当前活跃 session (去重: 同一用户只计一次) */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /** 所有 WebSocket 会话 */
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public void add(WebSocketSession session, Map<String, Object> userInfo) {
        Long userId = ((Number) userInfo.get("userId")).longValue();
        // 同一用户再次连接: 关闭旧会话, 避免重复统计
        WebSocketSession old = userSessions.put(userId, session);
        if (old != null && old != session) {
            if (old.isOpen()) {
                try { old.close(CloseStatus.NORMAL.withReason("replaced by new session")); } catch (IOException ignored) {}
            }
            sessions.remove(old);
            sessionUsers.remove(old.getId());
        }
        sessions.add(session);
        sessionUsers.put(session.getId(), userInfo);
        log.info("WebSocket 上线: accountNo={}, role={}, 当前在线 {}",
                userInfo.get("accountNo"), userInfo.get("role"), sessions.size());
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session);
        Map<String, Object> u = sessionUsers.remove(session.getId());
        if (u != null) {
            // 清理 userSession 映射: 只清理与当前 session 相同的引用, 避免误关新连接
            Long userId = ((Number) u.get("userId")).longValue();
            if (userSessions.get(userId) == session) {
                userSessions.remove(userId);
            }
            log.info("WebSocket 下线: accountNo={}, 当前在线 {}", u.get("accountNo"), sessions.size());
        }
    }

    /** 当前在线领导列表 (含用户信息, 按 userId 去重) */
    public List<Map<String, Object>> onlineLeaders() {
        return userSessions.values().stream()
                .map(s -> sessionUsers.get(s.getId()))
                .filter(u -> u != null && "leader".equals(u.get("role")))
                .toList();
    }

    public int onlineCount() {
        return userSessions.size();
    }

    /** 广播给所有在线会话 */
    public void broadcast(String payload) {
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (IOException e) {
                    log.warn("WS 推送失败: {}", e.getMessage());
                }
            }
        }
    }

    /** 广播给指定用户 (按 accountNo) */
    public void sendToUser(String accountNo, String payload) {
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            Map<String, Object> u = sessionUsers.get(s.getId());
            if (u != null && accountNo.equals(u.get("accountNo")) && s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (IOException e) {
                    log.warn("WS 推送失败: {}", e.getMessage());
                }
            }
        }
    }

    /** 广播 themeUpdate 通知: 关注该主题的客户端(投票端/控场端/大屏)刷新 */
    public void pushThemeUpdate(Long themeId) {
        pushThemeUpdate(themeId, null);
    }

    /** 广播 themeUpdate 通知(带动作标记): action 如 start/tally/end 等, 客户端可据此做定向响应 */
    public void pushThemeUpdate(Long themeId, String action) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "themeUpdate");
            msg.put("themeId", themeId);
            if (action != null) {
                msg.put("action", action);
            }
            broadcast(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("推送 themeUpdate 失败: {}", e.getMessage());
        }
    }
}
