package com.social.fanout.kafka.message.in;

import com.social.fanout.kafka.message.KafkaMessage;
import java.util.List;

/**
 * {@code match-fanout} envelope 의 payload — matching 의 MatchFanoutPayload 와 1:1.
 * channelId 는 백필 후에만 발행되므로 항상 채워져 있다.
 */
public record MatchFanoutRequest(
    String matchId,
    Long channelId,
    List<String> recipientIds
) implements KafkaMessage {}
