package com.social.message.outbox;

import com.social.common.ContentMessage;
import java.util.List;

public record OutboxPayload(
    String messageId,
    Long channelId,
    String senderId,
    String senderName,
    String content,
    Long createdAt,
    String clientMessageId,
    List<String> recipientIds
) {
    public static OutboxPayload of(ContentMessage contentMessage, List<String> recipientIds) {
        return new OutboxPayload(
            contentMessage.messageId(),
            contentMessage.channelId(),
            contentMessage.senderId(),
            contentMessage.senderName(),
            contentMessage.content(),
            contentMessage.createdAt(),
            contentMessage.clientMessageId(),
            recipientIds
        );
    }
}
