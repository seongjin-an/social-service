package com.social.fanout.config;

import com.social.fanout.kafka.KafkaConsumerAwareRebalanceListener;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${chatting.kafka.listeners.message.topic}")
    private String messageFanoutTopic;

    @Value("${chatting.kafka.listeners.message.partitions}")
    private int partitions;

    @Value("${chatting.kafka.listeners.message.replicasFactor}")
    private short replicasFactor;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bootstrap.servers", bootstrapServers);
        configs.put(AdminClientConfig.RETRIES_CONFIG, 5); // 실패 시 재시도 횟수
        configs.put(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG, 1000); // 실패 시 재시도 하기 전 1초 지연

        return new KafkaAdmin(configs);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaConsumerAwareRebalanceListener kafkaConsumerAwareRebalanceListener
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(consumerFactory);
        ContainerProperties containerProperties = containerFactory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setConsumerRebalanceListener(kafkaConsumerAwareRebalanceListener);

        return containerFactory;
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(DefaultKafkaProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public NewTopic messageFanoutTopic() {
        return TopicBuilder.name(messageFanoutTopic)
                .partitions(partitions)
                .replicas(replicasFactor)
                .build();
    }
}
