package com.social.connection.websocket.message.payload;

import com.social.connection.websocket.message.WebSocketMessage;

public record ReadPayload(Long channelId, Long messageId) implements WebSocketMessage {

}
