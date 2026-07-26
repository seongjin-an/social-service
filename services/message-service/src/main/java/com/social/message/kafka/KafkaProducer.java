package com.social.message.kafka;

import com.social.common.JsonUtil;
import com.social.message.kafka.message.KafkaInboundEnvelope;
import com.social.message.kafka.message.out.MessageFanoutPayload;
import com.social.message.kafka.message.out.ReadFanoutPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaProducer {

    private static final String CONTENT_MESSAGE_FANOUT_TYPE = "CONTENT_MESSAGE_FANOUT";
    private static final String READ_MESSAGE_FANOUT_TYPE = "READ_MESSAGE_FANOUT";

    @Value("${chatting.kafka.topics.message-fanout}")
    private String messageFanoutTopic;

    @Value("${chatting.kafka.topics.read-fanout}")
    private String readFanoutTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;

    public void sendMessageFanout(MessageFanoutPayload payload) {
        KafkaInboundEnvelope envelope = new KafkaInboundEnvelope(
            CONTENT_MESSAGE_FANOUT_TYPE,
            jsonUtil.convertJsonNode(payload).orElseThrow()
        );
        String json = jsonUtil.toJson(envelope).orElseThrow();
        kafkaTemplate.send(messageFanoutTopic, payload.channelId().toString(), json);
    }

    public void sendReadFanout(ReadFanoutPayload payload) {
        KafkaInboundEnvelope envelope = new KafkaInboundEnvelope(
            READ_MESSAGE_FANOUT_TYPE,
            jsonUtil.convertJsonNode(payload).orElseThrow()
        );
        String json = jsonUtil.toJson(envelope).orElseThrow();
        kafkaTemplate.send(readFanoutTopic, payload.channelId().toString(), json);
    }
}
