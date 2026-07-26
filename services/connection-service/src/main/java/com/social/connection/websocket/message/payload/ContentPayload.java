package com.social.connection.websocket.message.payload;

import com.social.connection.websocket.message.WebSocketMessage;

// clientMessageId: 클라이언트가 생성한 UUID — 재전송 시 동일 값을 보내 서버 측 중복 저장을 방지
public record ContentPayload(Long channelId, String content, String clientMessageId) implements WebSocketMessage {

}
