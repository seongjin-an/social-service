package com.social.message.kafka.message.in;

import java.util.List;
import java.util.UUID;

/**
 * {@code match-unmatched} 토픽 페이로드 — matching-service 의 MatchUnmatchedPayload 와 1:1 (raw JSON).
 * channelId 는 백필 전 언매치 같은 경계 상황에서 null 일 수 있어 matchId 로 채널을 찾는다.
 */
public record MatchUnmatchedRequest(UUID matchId, Long channelId, List<UUID> recipients) {}
