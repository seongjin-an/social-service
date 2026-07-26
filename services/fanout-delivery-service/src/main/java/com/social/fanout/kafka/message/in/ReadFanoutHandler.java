package com.social.fanout.kafka.message.in;

import com.social.common.KeyPrefix;
import com.social.fanout.kafka.KafkaProducer;
import com.social.fanout.kafka.message.KafkaMessageProcessor;
import com.social.fanout.kafka.message.KafkaMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.social.common.JsonUtil;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ReadFanoutHandler implements KafkaMessageProcessor<ReadFanoutRequest> {

    private final KafkaProducer kafkaProducer;
    private final StringRedisTemplate redisTemplate;
    private final JsonUtil jsonUtil;

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
        String userKey = KeyPrefix.WEBSOCKET_USER + recipientUserId;
        Set<String> connectionKeys = redisTemplate.opsForSet().members(userKey);
        if (connectionKeys == null || connectionKeys.isEmpty()) {
            return;
        }

        Set<String> instanceIds = new HashSet<>();
        for (String connectionKey : connectionKeys) {
            String connectionInfoJson = redisTemplate.opsForValue()
                .get(KeyPrefix.WEBSOCKET_CONNECTION + connectionKey);
            if (connectionInfoJson == null) {
                redisTemplate.opsForSet().remove(userKey, connectionKey);
                continue;
            }
            jsonUtil.fromJson(connectionInfoJson, JsonNode.class)
                .map(node -> node.get("instanceId"))
                .map(JsonNode::asText)
                .ifPresent(instanceIds::add);
        }

        instanceIds.forEach(instanceId ->
            kafkaProducer.sendReadEvent(instanceId, recipientUserId, channelId, readerId, lastReadMessageId));
    }
}
