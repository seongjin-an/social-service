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

    /**
     * 매칭 성사 알림 fanout. 여기서도 실패하든 말든 ack 한다 — 알림은 유실돼도 매칭 목록으로 확인되므로
     * 재시도로 컨슈머를 막는 것보다 흘려보내는 게 낫다(다른 파티션의 알림까지 지연되면 손해가 크다).
     */
    @KafkaListener(
        topics = "${chatting.kafka.listeners.match.topic}",
        groupId = "${chatting.kafka.listeners.match.group}",
        concurrency = "${chatting.kafka.listeners.match.concurrency}")
    public void consumeMatchFanout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            kafkaMessageDispatcher.dispatch(record.value());
        } catch (Exception e) {
            log.error("[FanoutConsumer] match 처리 실패 key={}", record.key(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
