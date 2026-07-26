package com.social.connection.kafka.message.out;

import com.social.connection.kafka.message.KafkaMessage;

public record ReadContentMessageRequest(Long channelId, String userId, Long messageId) implements
    KafkaMessage {

    public static ReadContentMessageRequest create(Long channelId, String userId, Long messageId) {
        return new ReadContentMessageRequest(channelId, userId, messageId);
    }
}
