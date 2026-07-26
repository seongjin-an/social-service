package com.social.connection.websocket.message;

import com.social.common.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class WebSocketDispatcher {

    private final Map<WebSocketMessageType, WebSocketMessageProcessor<?>> handlerMap;
    private final JsonUtil jsonUtil;

    // Spring이 RequestHandler 구현체 빈을 자동으로 수집
    public WebSocketDispatcher(List<WebSocketMessageProcessor<?>> handlers, JsonUtil jsonUtil) {
        this.handlerMap = handlers.stream()
            .collect(Collectors.toMap(WebSocketMessageProcessor::getSupportedType, h -> h));
        this.jsonUtil = jsonUtil;
    }

    public void dispatch(WebSocketSession session, String rawJson) {
        try {
            WebSocketInboundEnvelope envelope = jsonUtil.fromJson(rawJson, WebSocketInboundEnvelope.class).orElseThrow();
            WebSocketMessageType type = WebSocketMessageType.valueOf(envelope.type());

            WebSocketMessageProcessor<?> handler = handlerMap.get(type);
            if (handler == null) {
                log.warn("No handler registered for type: {}", type);
                return;
            }

            dispatchInternal(session, handler, envelope.payload());

        } catch (IllegalArgumentException e) {
            log.warn("Unknown message type: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Dispatch failed: {}", e.getMessage());
        }
    }

    // <T>를 여기서 새로 선언하는 것이 핵심
    // handlerMap에서 꺼낼 때는 타입이 지워지지만(RequestHandler<?>),
    // 이 메서드 안에서 T로 바인딩되므로 getPayloadType()과 handle()이 타입 안전하게 연결됨
    private <T extends WebSocketMessage> void dispatchInternal(
        WebSocketSession session, WebSocketMessageProcessor<T> handler, JsonNode payload) {
        T message = jsonUtil.fromJson(payload.toString(), handler.getPayloadType()).orElseThrow();
        handler.handle(session, message);
    }
}
