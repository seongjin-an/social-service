package com.social.message.kafka;

import com.social.message.kafka.message.KafkaMessageDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumer {

    private final KafkaMessageDispatcher kafkaMessageDispatcher;

    @KafkaListener(
        topics = "${chatting.kafka.listeners.message.topic}",
        groupId = "${chatting.kafka.listeners.message.group}",
        concurrency = "${chatting.kafka.listeners.message.concurrency}")
    public void consumeMessageRelay(
        ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        kafkaMessageDispatcher.dispatch(consumerRecord.value());

        acknowledgment.acknowledge();
    }

    @KafkaListener(
        topics = "${chatting.kafka.listeners.read.topic}",
        groupId = "${chatting.kafka.listeners.read.group}",
        concurrency = "${chatting.kafka.listeners.read.concurrency}")
    public void consumeReadRelay(
        ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        kafkaMessageDispatcher.dispatch(consumerRecord.value());

        acknowledgment.acknowledge();
    }
}
