package com.social.message.kafka.message.out;

import java.util.List;
import java.util.UUID;

/**
 * {@code channel-created} 토픽 페이로드 (raw JSON) — matching-service 가 matches.channel_id 백필에 쓴다.
 * saga 상관키는 matchId(= outbox partition_key)라 matching 은 이걸로 매칭을 찾는다.
 */
public record ChannelCreatedPayload(UUID matchId, Long channelId, List<UUID> recipients) {

    public static ChannelCreatedPayload of(UUID matchId, Long channelId, List<UUID> recipients) {
        return new ChannelCreatedPayload(matchId, channelId, recipients);
    }
}
