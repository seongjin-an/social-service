package com.social.message.service.message;

import com.social.message.domain.MessageEntity;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Message {
    private final Long channelId;
    private final String messageId; // DB는 Long(BIGINT), JSON 직렬화는 String으로 정밀도 유지
    private final String userId;
    private final String userName;
    private final String content;
    private final long createdAt; // epoch ms

    public static Message of(MessageEntity entity) {
        long createdAt = entity.getCreatedAt() != null
            ? entity.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
            : System.currentTimeMillis();
        return new Message(
            entity.getChannelId(),
            String.valueOf(entity.getMessageId()),
            entity.getUserId().toString(),
            entity.getUserName(),
            entity.getContent(),
            createdAt
        );
    }
}
