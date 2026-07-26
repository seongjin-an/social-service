package com.social.fanout.kafka.message.in;

import com.social.fanout.kafka.message.KafkaMessage;
import java.util.List;

public record MessageFanoutRequest(
    String messageId,
    Long channelId,
    String senderId,
    String senderName,
    String content,
    Long createdAt,
    String clientMessageId,
    List<String> recipientIds
) implements KafkaMessage {}
