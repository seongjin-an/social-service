package com.social.message.kafka.message.in;

import com.social.message.kafka.message.KafkaMessage;

public record ReadRelayRequest(Long channelId, String userId, Long messageId) implements
    KafkaMessage {

}
