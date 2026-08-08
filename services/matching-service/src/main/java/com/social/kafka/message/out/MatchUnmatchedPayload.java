package com.social.kafka.message.out;

import java.util.List;
import java.util.UUID;

/**
 * {@code match-unmatched} 토픽 페이로드 (raw JSON) — message-service 가 채널을 CLOSED 로 닫는다.
 * 채널 백필 전에 언매치하면 channelId 가 null 일 수 있어, 소비 측은 matchId 로 채널을 찾는다.
 */
public record MatchUnmatchedPayload(UUID matchId, Long channelId, List<UUID> recipients) {

    public static MatchUnmatchedPayload of(UUID matchId, Long channelId, List<UUID> recipients) {
        return new MatchUnmatchedPayload(matchId, channelId, recipients);
    }
}
