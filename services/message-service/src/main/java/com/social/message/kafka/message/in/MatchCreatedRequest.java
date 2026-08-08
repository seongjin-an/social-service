package com.social.message.kafka.message.in;

import java.util.List;
import java.util.UUID;

/**
 * {@code match-created} 토픽 페이로드 — matching-service 의 MatchCreatedPayload 와 1:1.
 *
 * <p>이 토픽은 <b>envelope 없이 raw JSON</b> 이다(단일 타입 토픽이라 type 필드로 라우팅할 게 없음).
 * 그래서 기존 {@code KafkaMessageDispatcher}(envelope 기반)를 타지 않고 전용 리스너에서 직접 파싱한다.
 */
public record MatchCreatedRequest(UUID matchId, UUID lo, UUID hi, List<UUID> recipients) {}
