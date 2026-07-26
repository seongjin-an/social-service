package com.social.fanout.kafka.message;

public interface KafkaMessageProcessor<T extends KafkaMessage> {

    KafkaMessageType getSupportedType();
    Class<T> getPayloadType();
    void handle(T message);
}
