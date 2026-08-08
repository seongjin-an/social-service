package com.social.recommendation.kafka.message.in;

import java.util.UUID;

/**
 * {@code like-relay} 토픽 페이로드 (raw JSON) — matching 의 LikeRelayPayload 와 1:1.
 *
 * <p>{@code type} 을 enum 이 아니라 String 으로 받는 이유: recommendation 은 LIKE/PASS/SUPER 를
 * <b>구분하지 않는다</b>(어느 쪽이든 "이미 본 사람"이다). 타입이 하나 추가돼도 이 서비스는 깨지지 않아야 한다.
 */
public record LikeRelayRequest(UUID fromUserId, UUID toUserId, String type) {}
