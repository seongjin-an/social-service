package com.social.message.service.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class MessageCursorRequest {
    private final Long channelId;
    private final Long messageId; // cursor nextKey 역할인데 null / -1 일 경우 끝임.
    private final int offset;
    private final int size;

    private MessageCursorRequest(Long channelId) {
        this(channelId, null, 0, 20);
    }

    private MessageCursorRequest(Long channelId, Long messageId) {
        this(channelId, messageId, 0, 20);
    }


    public static MessageCursorRequest of(Long channelId) {
        return new MessageCursorRequest(channelId);
    }

    public static MessageCursorRequest of(Long channelId, Long messageId) {
        return new MessageCursorRequest(channelId, messageId);
    }

    public static MessageCursorRequest of(Long channelId, Long messageId, int offset, int size) {
        return new MessageCursorRequest(channelId, messageId, offset, size);
    }
}
