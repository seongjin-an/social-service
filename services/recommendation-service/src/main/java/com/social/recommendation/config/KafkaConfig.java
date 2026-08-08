package com.social.recommendation.config;

import com.social.recommendation.kafka.KafkaConsumerAwareRebalanceListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * 컨슈머 전용 설정 — recommendation 은 이벤트를 <b>소비만</b> 한다(발행할 이벤트가 없다).
 * 그래서 다른 서비스와 달리 KafkaTemplate 빈을 두지 않는다.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory,
        KafkaConsumerAwareRebalanceListener kafkaConsumerAwareRebalanceListener
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory =
            new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(consumerFactory);

        ContainerProperties containerProperties = containerFactory.getContainerProperties();
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setConsumerRebalanceListener(kafkaConsumerAwareRebalanceListener);

        return containerFactory;
    }
}
