package com.social.connection.kafka.message.in;

import com.social.common.JsonUtil;
import com.social.common.UserId;
import com.social.connection.kafka.message.KafkaMessageProcessor;
import com.social.connection.kafka.message.KafkaMessageType;
import com.social.connection.websocket.WebSocketSessionRegistry;
import com.social.connection.websocket.message.WebSocketOutboundEnvelope;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 매칭 성사 알림을 해당 유저의 WS 세션(들)로 push.
 *
 * <p>클라이언트가 받는 형태: {@code {"type":"MATCH_NOTIFICATION","payload":{userId, matchId, channelId}}}
 * → channelId 가 들어 있으므로 알림 하나로 바로 채팅방을 열 수 있다.
 *
 * <p>세션이 없으면 그냥 버린다 — 이 인스턴스로 라우팅된 뒤 연결이 끊긴 경우이고, 매칭 자체는
 * 매칭 목록 API 로 확인 가능하다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MatchNotificationHandler implements KafkaMessageProcessor<MatchNotification> {

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final JsonUtil jsonUtil;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.MATCH_NOTIFICATION;
    }

    @Override
    public Class<MatchNotification> getPayloadType() {
        return MatchNotification.class;
    }

    @Override
    public void handle(MatchNotification message) {
        UserId userId = UserId.of(message.userId());
        Map<String, WebSocketSession> userSessions = webSocketSessionRegistry.getSessions(userId);

        if (userSessions.isEmpty()) {
            log.debug("No active sessions for match notification: userId={}", message.userId());
            return;
        }

        WebSocketOutboundEnvelope envelope =
            new WebSocketOutboundEnvelope(KafkaMessageType.MATCH_NOTIFICATION.name(), message);
        TextMessage textMessage = new TextMessage(jsonUtil.toJson(envelope).orElseThrow());

        for (WebSocketSession session : userSessions.values()) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.error("Failed to deliver match notification to userId={}, sessionId={}: {}",
                    message.userId(), session.getId(), e.getMessage());
            }
        }
    }
}
