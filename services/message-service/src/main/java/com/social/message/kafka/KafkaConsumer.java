package com.social.message.kafka;

import com.social.common.JsonUtil;
import com.social.message.kafka.message.KafkaMessageDispatcher;
import com.social.message.kafka.message.in.MatchCreatedRequest;
import com.social.message.kafka.message.in.MatchUnmatchedRequest;
import com.social.message.service.saga.MatchSagaService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumer {

    private final KafkaMessageDispatcher kafkaMessageDispatcher;
    private final MatchSagaService matchSagaService;
    private final JsonUtil jsonUtil;

    @KafkaListener(
        topics = "${chatting.kafka.listeners.message.topic}",
        groupId = "${chatting.kafka.listeners.message.group}",
        concurrency = "${chatting.kafka.listeners.message.concurrency}")
    public void consumeMessageRelay(
        ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        kafkaMessageDispatcher.dispatch(consumerRecord.value());

        acknowledgment.acknowledge();
    }

    @KafkaListener(
        topics = "${chatting.kafka.listeners.read.topic}",
        groupId = "${chatting.kafka.listeners.read.group}",
        concurrency = "${chatting.kafka.listeners.read.concurrency}")
    public void consumeReadRelay(
        ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        kafkaMessageDispatcher.dispatch(consumerRecord.value());

        acknowledgment.acknowledge();
    }

    /**
     * 매칭 Saga 1단계 수신 — DIRECT 채널을 만든다.
     *
     * <p>디스패처(envelope 기반)를 안 타고 직접 파싱하는 이유: match-created 는 타입이 하나뿐인
     * saga 토픽이라 envelope 없이 raw payload 로 흐른다(matching 의 outbox 가 그렇게 쓴다).
     *
     * <p><b>재시도 정책</b>은 실패 종류로 갈린다:
     * <ul>
     *   <li><b>못 읽는 레코드</b>(형식 오류·matchId 누락) → 재시도해도 절대 성공하지 않는다. 로그만 남기고
     *       ack 해서 건너뛴다. 안 그러면 파티션이 그 한 건에 붙잡혀 뒤에 온 정상 매칭까지 늦어진다.</li>
     *   <li><b>처리 실패</b>(DB 오류 등) → 예외를 그대로 올려 offset 을 커밋하지 않는다. 재배달되고
     *       핸들러가 멱등하므로 결국 채널이 만들어진다("유실 0"의 근거).</li>
     * </ul>
     */
    @KafkaListener(
        topics = "${chatting.kafka.listeners.match-created.topic}",
        groupId = "${chatting.kafka.listeners.match-created.group}",
        concurrency = "${chatting.kafka.listeners.match-created.concurrency}")
    public void consumeMatchCreated(
        ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        Optional<MatchCreatedRequest> parsed =
            jsonUtil.fromJson(consumerRecord.value(), MatchCreatedRequest.class)
                .filter(request -> request.matchId() != null);

        if (parsed.isEmpty()) {
            log.warn("[Saga] match-created 처리 불가 — 건너뜀: offset={}, value={}",
                consumerRecord.offset(), consumerRecord.value());
            acknowledgment.acknowledge();
            return;
        }

        matchSagaService.onMatchCreated(parsed.get());

        acknowledgment.acknowledge();
    }

    /** 언매치 수신 — 해당 매칭의 채널을 CLOSED 로 닫는다(멱등). 재시도 정책은 match-created 와 동일. */
    @KafkaListener(
        topics = "${chatting.kafka.listeners.match-unmatched.topic}",
        groupId = "${chatting.kafka.listeners.match-unmatched.group}",
        concurrency = "${chatting.kafka.listeners.match-unmatched.concurrency}")
    public void consumeMatchUnmatched(
        ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        Optional<MatchUnmatchedRequest> parsed =
            jsonUtil.fromJson(consumerRecord.value(), MatchUnmatchedRequest.class)
                .filter(request -> request.matchId() != null);

        if (parsed.isEmpty()) {
            log.warn("[Saga] match-unmatched 처리 불가 — 건너뜀: offset={}, value={}",
                consumerRecord.offset(), consumerRecord.value());
            acknowledgment.acknowledge();
            return;
        }

        matchSagaService.onMatchUnmatched(parsed.get());

        acknowledgment.acknowledge();
    }
}
