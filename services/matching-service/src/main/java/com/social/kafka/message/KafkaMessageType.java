package com.social.kafka.message;

/**
 * matching 이 관여하는 Kafka 메시지 타입.
 *
 * <p>{@code MATCH_FANOUT} 만 값이 실제로 와이어에 실린다(fanout 디스패처가 envelope.type 으로 라우팅).
 * 나머지 saga 토픽은 타입이 하나뿐이라 raw payload 로 흐르고, 타입 구분은 토픽 자체가 한다.
 */
public enum KafkaMessageType {
    LIKE_RELAY,
    MATCH_CREATED,
    CHANNEL_CREATED,
    MATCH_FANOUT,
    MATCH_UNMATCHED
}
