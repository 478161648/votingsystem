package com.voting.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 投票 WebSocket 处理器: 接收客户端订阅消息, 推送控场广播
 * 协议: 客户端发送 {type:"ping"} 保持连接; 服务端推送 {type:"themeUpdate",...}
 */
@Slf4j
@Component
public class VoteWebSocketHandler extends TextWebSocketHandler {

    private final WsSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VoteWebSocketHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", session.getAttributes().get("userId"));
        userInfo.put("accountNo", session.getAttributes().get("accountNo"));
        userInfo.put("role", session.getAttributes().get("role"));
        sessionManager.add(session, userInfo);

        // 连接建立后推送一次在线状态
        Map<String, Object> hello = new HashMap<>();
        hello.put("type", "hello");
        hello.put("accountNo", userInfo.get("accountNo"));
        hello.put("online", sessionManager.onlineCount());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(hello)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 心跳
        if ("ping".equals(payload) || "{\"type\":\"ping\"}".equals(payload)) {
            Map<String, Object> pong = new HashMap<>();
            pong.put("type", "pong");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 传输错误: {}", exception.getMessage());
        sessionManager.remove(session);
    }
}
