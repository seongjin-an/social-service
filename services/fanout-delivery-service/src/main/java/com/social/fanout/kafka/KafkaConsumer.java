package com.social.fanout.kafka;

import com.social.fanout.kafka.message.KafkaMessageDispatcher;
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
    public void consumeMessageFanout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            kafkaMessageDispatcher.dispatch(record.value());
        } catch (Exception e) {
            log.error("[FanoutConsumer] 처리 실패 key={}", record.key(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
        topics = "${chatting.kafka.listeners.read.topic}",
        groupId = "${chatting.kafka.listeners.read.group}",
        concurrency = "${chatting.kafka.listeners.read.concurrency}")
    public void consumeReadFanout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            kafkaMessageDispatcher.dispatch(record.value());
        } catch (Exception e) {
            log.error("[FanoutConsumer] read 처리 실패 key={}", record.key(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
