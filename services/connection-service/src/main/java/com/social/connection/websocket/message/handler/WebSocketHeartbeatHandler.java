package com.social.connection.websocket.message.handler;

import com.social.common.Constant;
import com.social.common.UserId;
import com.social.connection.service.CacheService;
import com.social.connection.websocket.message.WebSocketMessageProcessor;
import com.social.connection.websocket.message.payload.HeartbeatPayload;
import com.social.connection.websocket.message.WebSocketMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@RequiredArgsConstructor
@Component
public class WebSocketHeartbeatHandler implements WebSocketMessageProcessor<HeartbeatPayload> {

    private final CacheService cacheService;

    @Override
    public WebSocketMessageType getSupportedType() {
        return WebSocketMessageType.HEARTBEAT;
    }

    @Override
    public Class<HeartbeatPayload> getPayloadType() {
        return HeartbeatPayload.class;
    }

    @Override
    public void handle(WebSocketSession session, HeartbeatPayload message) {
        String connectionKey = (String) session.getAttributes().get(Constant.WEBSOCKET_CONNECTION_KEY);
        UserId userId = UserId.of((String) session.getAttributes().get(Constant.USER_ID));
        cacheService.refreshConnectionTtl(userId, connectionKey);
    }
}
