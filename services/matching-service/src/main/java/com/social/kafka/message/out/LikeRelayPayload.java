package com.social.kafka.message.out;

import com.social.domain.like.LikeType;
import java.util.UUID;

public record LikeRelayPayload(UUID fromUserId, UUID toUserId, LikeType type) {

    public static LikeRelayPayload of(UUID fromUserId, UUID toUserId, LikeType type) {
        return new LikeRelayPayload(fromUserId, toUserId, type);
    }
}
