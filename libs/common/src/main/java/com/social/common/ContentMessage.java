package com.social.common;

public record ContentMessage(String messageId, Long channelId, String senderId, String senderName, String content, Long createdAt, String clientMessageId) {

    public static ContentMessage of(String messageId, Long channelId, String senderId, String senderName, String content, Long createdAt, String clientMessageId) {
        return new ContentMessage(messageId, channelId, senderId, senderName, content, createdAt, clientMessageId);
    }
}
