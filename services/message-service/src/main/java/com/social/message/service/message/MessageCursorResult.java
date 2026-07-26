package com.social.message.service.message;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class MessageCursorResult {
    private final long nextKey;
    private final List<Message> messages;

    public static MessageCursorResult of(long nextKey, List<Message> messages) {
        return new MessageCursorResult(nextKey, messages);
    }
}
