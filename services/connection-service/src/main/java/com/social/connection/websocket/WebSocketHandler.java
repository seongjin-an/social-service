package com.social.connection.websocket;

import com.social.common.Constant;
import com.social.common.UserId;
import com.social.connection.dto.ConnectionInfo;
import com.social.connection.service.CacheService;
import com.social.connection.websocket.message.WebSocketDispatcher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final CacheService cacheService;
    private final WebSocketDispatcher webSocketDispatcher;

    @Value("${server.id}")
    private String serverId;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        UserId userId = extractUserId(session);

        String connectionKey = UUID.randomUUID().toString();

        ConnectionInfo connectionInfo = ConnectionInfo.of(userId.id(), serverId, connectionKey, session.getId(), System.currentTimeMillis());
        session.getAttributes().put(Constant.WEBSOCKET_CONNECTION_KEY, connectionKey);

        ConcurrentWebSocketSessionDecorator concurrentWebSocketSessionDecorator =
            new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);

        webSocketSessionRegistry.putSession(userId, concurrentWebSocketSessionDecorator);
        cacheService.saveConnectionInfo(userId, connectionKey, connectionInfo);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        closeSession(session);
    }

    private void closeSession(WebSocketSession session) {
        UserId userId = extractUserId(session);
        webSocketSessionRegistry.closeSession(userId, session);

        String connectionKey = (String) session.getAttributes().get(Constant.WEBSOCKET_CONNECTION_KEY);
        cacheService.removeConnectionInfo(userId, connectionKey);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
        throws Exception {
        closeSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws Exception {
        webSocketDispatcher.dispatch(session, message.getPayload());
    }

    private UserId extractUserId(WebSocketSession session) {
        String userId = (String) session.getAttributes().get(Constant.USER_ID);
        return UserId.of(userId);
    }


}
