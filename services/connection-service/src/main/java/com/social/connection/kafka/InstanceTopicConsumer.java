package com.social.connection.kafka;

import com.social.common.JsonUtil;
import com.social.connection.kafka.message.KafkaMessage;
import com.social.connection.kafka.message.KafkaMessageDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InstanceTopicConsumer {

    private final InstanceTopicCreator instanceTopicCreator;
    private final KafkaMessageDispatcher kafkaMessageDispatcher;
    private final JsonUtil jsonUtil;

    @KafkaListener(
        topics = "#{__listener.getInstanceTopic()}",
        groupId = "#{__listener.getConsumerGroup()}",
        concurrency = "${chatting.kafka.listeners.connection.concurrency}"
    )
    public void consumeInstanceTopic(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        kafkaMessageDispatcher.dispatch(consumerRecord.value());

        acknowledgment.acknowledge();
    }

    public String getInstanceTopic() {
        return instanceTopicCreator.getInstanceTopic();
    }

    public String getConsumerGroup() {
        return instanceTopicCreator.getConsumerGroup();
    }
}
