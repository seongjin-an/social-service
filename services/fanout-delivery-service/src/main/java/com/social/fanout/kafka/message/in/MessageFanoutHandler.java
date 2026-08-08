package com.social.fanout.kafka.message.in;

import com.social.common.ContentMessage;
import com.social.common.KeyPrefix;
import com.social.fanout.kafka.KafkaProducer;
import com.social.fanout.kafka.message.KafkaMessageProcessor;
import com.social.fanout.kafka.message.KafkaMessageType;
import com.social.fanout.routing.ConnectionRoute;
import com.social.fanout.routing.ConnectionRouteResolver;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class MessageFanoutHandler implements KafkaMessageProcessor<MessageFanoutRequest> {

    private final KafkaProducer kafkaProducer;
    private final StringRedisTemplate redisTemplate;
    private final ConnectionRouteResolver connectionRouteResolver;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.CONTENT_MESSAGE_FANOUT;
    }

    @Override
    public Class<MessageFanoutRequest> getPayloadType() {
        return MessageFanoutRequest.class;
    }

    @Override
    public void handle(MessageFanoutRequest request) {
        ContentMessage contentMessage = ContentMessage.of(
            request.messageId(),
            request.channelId(),
            request.senderId(),
            request.senderName(),
            request.content(),
            request.createdAt(),
            request.clientMessageId()
        );

        request.recipientIds().forEach(userId -> route(userId, contentMessage));
    }

    private void route(String userId, ContentMessage contentMessage) {
        ConnectionRoute connectionRoute = connectionRouteResolver.resolve(userId);

        if (!connectionRoute.online()) {
            // 오프라인 유저 — 미읽음 카운터 증가
            // count == 1 이면 키가 새로 생성된 것 → TTL 30일 설정 (READ로 삭제 안 되더라도 자동 정리)
            String unreadKey = KeyPrefix.UNREAD_COUNT + userId + ":" + contentMessage.channelId();
            Long count = redisTemplate.opsForValue().increment(unreadKey);
            if (Long.valueOf(1L).equals(count)) {
                redisTemplate.expire(unreadKey, 30, TimeUnit.DAYS);
            }
            return;
        }

        connectionRoute.instanceIds().forEach(instanceId ->
                kafkaProducer.sendContentMessageResponse(instanceId, userId, contentMessage));
    }
}
