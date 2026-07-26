package com.social.fanout.kafka;

import com.social.common.ContentMessage;
import com.social.common.JsonUtil;
import com.social.fanout.kafka.message.KafkaEnvelope;
import com.social.fanout.kafka.message.out.ContentMessageResponsePayload;
import com.social.fanout.kafka.message.out.ReadEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaProducer {

    private static final String CONTENT_MESSAGE_RESPONSE_TYPE = "CONTENT_MESSAGE_RESPONSE";
    private static final String READ_EVENT_TYPE = "READ_EVENT";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    @Value("${chatting.kafka.topics.connection-instance-prefix}")
    private String connectionInstancePrefix;

    public void sendContentMessageResponse(String instanceId, String userId, ContentMessage message) {
        ContentMessageResponsePayload payload = new ContentMessageResponsePayload(userId, message);
        KafkaEnvelope envelope = new KafkaEnvelope(
            CONTENT_MESSAGE_RESPONSE_TYPE,
            jsonUtil.convertJsonNode(payload).orElseThrow()
        );
        String json = jsonUtil.toJson(envelope).orElseThrow();
        kafkaTemplate.send(connectionInstancePrefix + instanceId, userId, json);
    }

    public void sendReadEvent(String instanceId, String recipientUserId,
        Long channelId, String readerId, Long lastReadMessageId) {
        ReadEventPayload payload = new ReadEventPayload(recipientUserId, channelId, readerId, lastReadMessageId);
        KafkaEnvelope envelope = new KafkaEnvelope(
            READ_EVENT_TYPE,
            jsonUtil.convertJsonNode(payload).orElseThrow()
        );
        String json = jsonUtil.toJson(envelope).orElseThrow();
        kafkaTemplate.send(connectionInstancePrefix + instanceId, recipientUserId, json);
    }
}
