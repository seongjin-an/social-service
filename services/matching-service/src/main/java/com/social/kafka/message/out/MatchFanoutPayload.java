package com.social.kafka.message.out;

import java.util.List;
import java.util.UUID;

/**
 * {@code match-fanout} 토픽 페이로드 — fanout-delivery-service 가 수신자별 WS 라우팅에 쓴다.
 *
 * <p>userId 를 <b>String</b> 으로 담는 이유: fanout 이 Redis 키({@code ws:user:{userId}})를
 * 문자열로 조립하고 connection 토픽의 메시지 키로도 그대로 쓴다 — 기존 메시지 fanout 과 동일한 규약.
 *
 * <p>channelId 는 항상 채워져 있다(백필 후에만 발행하므로) → 알림만 받고 바로 채팅방을 열 수 있다.
 */
public record MatchFanoutPayload(
    UUID matchId,
    Long channelId,
    List<String> recipientIds
) {
    public static MatchFanoutPayload of(UUID matchId, Long channelId, List<UUID> recipients) {
        return new MatchFanoutPayload(matchId, channelId,
            recipients.stream().map(UUID::toString).toList());
    }
}
