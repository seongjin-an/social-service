package com.social.connection.lifecycle;

import com.social.common.Constant;
import com.social.common.UserId;
import com.social.connection.service.CacheService;
import com.social.connection.websocket.WebSocketSessionRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@RequiredArgsConstructor
@Component
public class GracefulShutdownHandler {

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final CacheService cacheService;

    @PreDestroy
    public void onShutdown() {
        log.info("Shutting down: cleaning up WebSocket sessions and Redis entries");

        webSocketSessionRegistry.getAllSessions().forEach((userId, sessionMap) ->
            sessionMap.values().forEach(session -> cleanup(userId, session))
        );
    }

    private void cleanup(UserId userId, WebSocketSession session) {
        String connectionKey = (String) session.getAttributes().get(Constant.WEBSOCKET_CONNECTION_KEY);

        if (connectionKey != null) {
            cacheService.removeConnectionInfo(userId, connectionKey);
        }

        try {
            if (session.isOpen()) {
                session.close(CloseStatus.GOING_AWAY);
            }
        } catch (IOException e) {
            log.error("Failed to close session={} on shutdown: {}", session.getId(), e.getMessage());
        }
    }
}
