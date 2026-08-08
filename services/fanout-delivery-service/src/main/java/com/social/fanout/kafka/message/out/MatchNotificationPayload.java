package com.social.fanout.kafka.message.out;

/**
 * connection-instance-{id} 토픽으로 보내는 매칭 성사 알림.
 * userId 는 connection 이 "누구 세션으로 보낼지" 고르는 값이고, channelId 로 클라가 바로 채팅방을 연다.
 */
public record MatchNotificationPayload(String userId, String matchId, Long channelId) {}
