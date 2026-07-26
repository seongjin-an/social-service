package com.social.connection.websocket.message;

import com.fasterxml.jackson.databind.JsonNode;

// 클라이언트로부터 수신하는 메시지 봉투
// { "type": "SEND_MESSAGE", "payload": { ... } }
public record WebSocketInboundEnvelope(String type, JsonNode payload) {}
