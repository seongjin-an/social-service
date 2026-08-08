package com.social.kafka.message.out;

/**
 * fanout-delivery-service 로 보낼 때만 쓰는 봉투.
 *
 * <p>saga 토픽(match-created / channel-created / match-unmatched)은 타입이 하나뿐이라 raw JSON 으로
 * 흐르지만, fanout 은 <b>한 서비스가 여러 종류의 fanout 을 처리</b>하는 구조라
 * {@code KafkaMessageDispatcher} 가 {@code type} 으로 핸들러를 찾는다. 그래서 match-fanout 만
 * {@code {"type": ..., "payload": {...}}} 형태로 감싼다. (기존 message-fanout / read-fanout 과 동일 규약)
 */
public record FanoutEnvelope(String type, Object payload) {

    public static FanoutEnvelope of(String type, Object payload) {
        return new FanoutEnvelope(type, payload);
    }
}
