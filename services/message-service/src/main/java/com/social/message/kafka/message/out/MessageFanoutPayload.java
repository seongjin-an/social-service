package com.social.message.kafka.message.out;

import java.util.List;

public record MessageFanoutPayload(
    String eventId,
    Long channelId,
    String messageId,
    String senderId,
    String senderName,
    String content,
    String traceId,
    Long createdAt,
    List<String> recipientIds
) {}
