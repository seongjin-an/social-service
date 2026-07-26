package com.social.connection.websocket.message;

import org.springframework.web.socket.WebSocketSession;

public interface WebSocketMessageProcessor<T extends WebSocketMessage> {

    WebSocketMessageType getSupportedType();

    Class<T> getPayloadType();

    void handle(WebSocketSession session, T message);
}
