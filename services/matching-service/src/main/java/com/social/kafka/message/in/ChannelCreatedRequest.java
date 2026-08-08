package com.social.kafka.message.in;

import java.util.List;
import java.util.UUID;

/**
 * {@code channel-created} 토픽 페이로드 (raw JSON) — message-service 의 ChannelCreatedPayload 와 1:1.
 * saga 상관키는 matchId.
 */
public record ChannelCreatedRequest(UUID matchId, Long channelId, List<UUID> recipients) {}
