package com.social.message.kafka.message.in;

import com.social.message.kafka.message.KafkaMessage;

public record MessageRelayRequest(
    String senderId,
    String senderName,
    Long channelId,
    String content,
    String instanceId,
    String clientMessageId
) implements KafkaMessage {}
