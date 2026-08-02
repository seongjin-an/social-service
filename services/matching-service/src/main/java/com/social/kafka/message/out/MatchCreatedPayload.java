package com.social.kafka.message.out;

import java.util.List;
import java.util.UUID;

public record MatchCreatedPayload(UUID matchId, UUID lo, UUID hi, List<UUID> recipients) {

    public static MatchCreatedPayload of(UUID matchId, UUID lo, UUID hi, List<UUID> recipients) {
        return new MatchCreatedPayload(matchId, lo, hi, recipients);
    }
}
