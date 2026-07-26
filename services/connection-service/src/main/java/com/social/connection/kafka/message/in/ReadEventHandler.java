package com.social.connection.kafka.message.in;

import com.social.common.JsonUtil;
import com.social.common.UserId;
import com.social.connection.kafka.message.KafkaMessageProcessor;
import com.social.connection.kafka.message.KafkaMessageType;
import com.social.connection.websocket.WebSocketSessionRegistry;
import com.social.connection.websocket.message.WebSocketOutboundEnvelope;
import com.social.connection.websocket.message.payload.ReadEventWsPayload;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReadEventHandler implements KafkaMessageProcessor<ReadEvent> {

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final JsonUtil jsonUtil;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.READ_EVENT;
    }

    @Override
    public Class<ReadEvent> getPayloadType() {
        return ReadEvent.class;
    }

    @Override
    public void handle(ReadEvent message) {
        UserId userId = UserId.of(message.userId());
        Map<String, WebSocketSession> userSessions = webSocketSessionRegistry.getSessions(userId);

        if (userSessions.isEmpty()) {
            return;
        }

        // 브라우저에 전달할 페이로드: 누가(readerId) 어느 채널(channelId)을 어디까지(lastReadMessageId) 읽었는지
        WebSocketOutboundEnvelope envelope = new WebSocketOutboundEnvelope(
            "READ_EVENT",
            new ReadEventWsPayload(message.channelId(), message.readerId(), message.lastReadMessageId())
        );
        String payload = jsonUtil.toJson(envelope).orElseThrow();
        TextMessage textMessage = new TextMessage(payload);

        userSessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.error("Failed to deliver READ_EVENT to userId={}, sessionId={}: {}",
                    message.userId(), session.getId(), e.getMessage());
            }
        });
    }
}
