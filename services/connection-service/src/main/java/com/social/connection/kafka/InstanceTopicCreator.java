package com.social.connection.kafka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InstanceTopicCreator {

    private final KafkaAdmin kafkaAdmin;
    private final String serverId;
    private final String topic;
    private final String group;
    private final String partitions;
    private final String replicasFactor;
    private final String retentionMs;

    public InstanceTopicCreator(
        KafkaAdmin kafkaAdmin,
        @Value("${server.id}") String serverId,
        @Value("${chatting.kafka.listeners.connection.topic}") String topic,
        @Value("${chatting.kafka.listeners.connection.group}") String group,
        @Value("${chatting.kafka.listeners.connection.partitions}") String partitions,
        @Value("${chatting.kafka.listeners.connection.replicasFactor}") String replicasFactor,
        @Value("${chatting.kafka.listeners.connection.retentionMs}") String retentionMs
    ) {
        this.kafkaAdmin = kafkaAdmin;
        this.serverId = serverId;
        this.topic = topic;
        this.group = group;
        this.partitions = partitions;
        this.replicasFactor = replicasFactor;
        this.retentionMs = retentionMs;

        initTopic();
    }

    private void initTopic() {
        String instanceTopic = getInstanceTopic();

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            NewTopic newTopic = new NewTopic(instanceTopic, Integer.parseInt(partitions), Short.parseShort(replicasFactor))
                .configs(Map.of("retention.ms", retentionMs));

            CreateTopicsResult topicsResult = adminClient.createTopics(List.of(newTopic));

            processTopicResult(instanceTopic, topicsResult);
        }
    }

    private void processTopicResult(String instanceTopic, CreateTopicsResult topicsResult) {
        topicsResult.values().forEach((topic, future) -> {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    log.info("Already existing topic: {}", instanceTopic);
                } else {
                    String message = "Create topic failed. topic: %s, cause: %s".formatted(instanceTopic, e.getMessage());
                    log.error(message);
                    throw new RuntimeException(message, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }

    public String getInstanceTopic() {
        return "%s-%s".formatted(topic, serverId);
    }

    public String getConsumerGroup() {
        return "%s-%s".formatted(group, serverId);
    }
}
