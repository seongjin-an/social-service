package com.social.fanout.kafka.message;

import com.social.common.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaMessageDispatcher {

    private final Map<KafkaMessageType, KafkaMessageProcessor<?>> handlerMap;
    private final JsonUtil jsonUtil;

    public KafkaMessageDispatcher(List<KafkaMessageProcessor<?>> kafkaMessageProcessors, JsonUtil jsonUtil) {
        this.handlerMap = kafkaMessageProcessors.stream()
            .collect(Collectors.toMap(KafkaMessageProcessor::getSupportedType, Function.identity()));
        this.jsonUtil = jsonUtil;
    }

    public void dispatch(String rawJson) {
        try {
            KafkaEnvelope kafkaInboundEnvelope = jsonUtil.fromJson(rawJson,
                KafkaEnvelope.class).orElseThrow();

            KafkaMessageType kafkaMessageType = KafkaMessageType.valueOf(
                kafkaInboundEnvelope.type());
            KafkaMessageProcessor<?> kafkaMessageProcessor = handlerMap.get(kafkaMessageType);
            if (kafkaMessageProcessor == null) {
                log.warn("No handler registered for type: {}", kafkaMessageType);
                return;
            }

            dispatchInternal(kafkaMessageProcessor, kafkaInboundEnvelope.payload());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown message type: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Dispatch failed: {}", e.getMessage());
        }
    }

    private <T extends KafkaMessage> void dispatchInternal(KafkaMessageProcessor<T> kafkaMessageProcessor,
        JsonNode payload) {
        T message = jsonUtil.fromJson(payload.toString(), kafkaMessageProcessor.getPayloadType())
            .orElseThrow();
        kafkaMessageProcessor.handle(message);
    }
}
