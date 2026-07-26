package com.social.connection.kafka.message.out;

import com.social.common.UserId;
import com.social.connection.kafka.message.KafkaMessage;
import com.social.connection.websocket.message.payload.ContentPayload;
import lombok.Builder;

@Builder
public record ContentMessageRequest(String senderId, String senderName, Long channelId, String content, String instanceId, String clientMessageId) implements
    KafkaMessage {

    public static ContentMessageRequest of(UserId senderId, String senderName, ContentPayload payload, String instanceId) {
        return new ContentMessageRequest(senderId.id(), senderName, payload.channelId(), payload.content(), instanceId, payload.clientMessageId());
    }
}
