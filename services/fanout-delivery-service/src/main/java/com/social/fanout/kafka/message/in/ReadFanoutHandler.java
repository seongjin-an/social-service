package com.social.fanout.kafka.message.in;

import com.social.fanout.kafka.KafkaProducer;
import com.social.fanout.kafka.message.KafkaMessageProcessor;
import com.social.fanout.kafka.message.KafkaMessageType;
import com.social.fanout.routing.ConnectionRouteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReadFanoutHandler implements KafkaMessageProcessor<ReadFanoutRequest> {

    private final KafkaProducer kafkaProducer;
    private final ConnectionRouteResolver connectionRouteResolver;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.READ_MESSAGE_FANOUT;
    }

    @Override
    public Class<ReadFanoutRequest> getPayloadType() {
        return ReadFanoutRequest.class;
    }

    @Override
    public void handle(ReadFanoutRequest request) {
        request.recipientIds().forEach(recipientUserId ->
            route(recipientUserId, request.channelId(), request.userId(), request.lastReadMessageId()));
    }

    private void route(String recipientUserId, Long channelId, String readerId, Long lastReadMessageId) {
        // 읽음 이벤트는 오프라인이면 보낼 필요가 없다(다음 조회 때 unreadCount 로 반영됨).
        connectionRouteResolver.resolve(recipientUserId).instanceIds().forEach(instanceId ->
            kafkaProducer.sendReadEvent(instanceId, recipientUserId, channelId, readerId, lastReadMessageId));
    }
}
