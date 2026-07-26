package com.social.connection.kafka;

import com.social.common.JsonUtil;
import com.social.connection.kafka.message.KafkaEnvelope;
import com.social.connection.kafka.message.KafkaMessage;
import com.social.connection.kafka.message.KafkaMessageType;
import com.social.connection.kafka.message.out.ContentMessageRequest;
import com.social.connection.kafka.message.out.ReadContentMessageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaProducer {

    private final String messageTopic;
    private final String readTopic;
    private final JsonUtil jsonUtil;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(
        @Value("${chatting.kafka.topics.message}") String messageTopic,
        @Value("${chatting.kafka.topics.read}") String readTopic,
        JsonUtil jsonUtil,
        KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.messageTopic = messageTopic;
        this.readTopic = readTopic;
        this.jsonUtil = jsonUtil;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendContentPayloadByPartitionKey(ContentMessageRequest contentMessageRequest) {

        KafkaEnvelope kafkaEnvelope = getKafkaEnvelope(
            KafkaMessageType.CONTENT_MESSAGE_RELAY.name(), contentMessageRequest);
        String value = jsonUtil.toJson(kafkaEnvelope).orElseThrow();
        String partitionKey = "%d-%s".formatted(contentMessageRequest.channelId(),
            contentMessageRequest.senderId());
        kafkaTemplate.send(messageTopic, partitionKey, value);
    }

    public void sendReadRelay(ReadContentMessageRequest readContentMessageRequest) {
        KafkaEnvelope kafkaEnvelope = getKafkaEnvelope(KafkaMessageType.READ_MESSAGE_RELAY.name(),
            readContentMessageRequest);
        String value = jsonUtil.toJson(kafkaEnvelope).orElseThrow();
        kafkaTemplate.send(readTopic, readContentMessageRequest.channelId().toString(), value);
    }

    private <T extends KafkaMessage> KafkaEnvelope getKafkaEnvelope(String type, T kafkaMessage) {
        JsonNode jsonNode = jsonUtil.convertJsonNode(kafkaMessage).orElseThrow();
        return new KafkaEnvelope(type, jsonNode);
    }
}
