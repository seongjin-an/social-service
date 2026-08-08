package com.social.connection.kafka.message.in;

import com.social.connection.kafka.message.KafkaMessage;

/** fanout 이 보낸 매칭 성사 알림 — fanout 의 MatchNotificationPayload 와 1:1. */
public record MatchNotification(String userId, String matchId, Long channelId) implements KafkaMessage {}
