package com.social.service.outbox;

import com.social.common.JsonUtil;
import com.social.domain.outbox.OutboxEventEntity;
import com.social.kafka.message.KafkaMessageType;
import com.social.kafka.message.out.FanoutEnvelope;
import com.social.kafka.message.out.MatchCreatedPayload;
import com.social.kafka.message.out.MatchFanoutPayload;
import com.social.kafka.message.out.MatchUnmatchedPayload;
import com.social.repository.outbox.OutboxEventRepository;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공용 {@code social.outbox} 에 매칭 도메인 이벤트를 적재한다. 실제 발행은 Debezium EventRouter 가
 * {@code destination_topic} 컬럼을 보고 해당 토픽으로 라우팅한다(커넥터 설정 변경 불필요).
 *
 * <p>모든 메서드가 {@link Propagation#MANDATORY} 인 이유: outbox 행은 <b>도메인 변경과 같은 트랜잭션</b>
 * 에서만 의미가 있다(dual-write 어긋남 방지). 트랜잭션 없이 호출되면 조용히 단독 커밋되는 대신
 * 즉시 예외로 실수를 드러낸다.
 *
 * <p>{@code partition_key} 는 전부 <b>matchId</b> — 한 매칭의 saga 이벤트들이 같은 파티션에서
 * 순서를 유지한다(MATCH_CREATED → CHANNEL_CREATED → MATCH_FANOUT → MATCH_UNMATCHED).
 */
@Slf4j
@Service
public class OutboxEventWriter {

    private static final String AGGREGATE_TYPE = "MATCH";

    private final String matchCreatedTopic;
    private final String matchFanoutTopic;
    private final String matchUnmatchedTopic;
    private final JsonUtil jsonUtil;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventWriter(
        @Value("${social.kafka.topics.match-created}") String matchCreatedTopic,
        @Value("${social.kafka.topics.match-fanout}") String matchFanoutTopic,
        @Value("${social.kafka.topics.match-unmatched}") String matchUnmatchedTopic,
        JsonUtil jsonUtil,
        OutboxEventRepository outboxEventRepository
    ) {
        this.matchCreatedTopic = matchCreatedTopic;
        this.matchFanoutTopic = matchFanoutTopic;
        this.matchUnmatchedTopic = matchUnmatchedTopic;
        this.jsonUtil = jsonUtil;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeMatchCreated(UUID matchId, UUID lo, UUID hi, List<UUID> recipients) {
        MatchCreatedPayload payload = MatchCreatedPayload.of(matchId, lo, hi, recipients);

        String json = jsonUtil.toJson(payload).orElseThrow();

        OutboxEventEntity outboxEventEntity = OutboxEventEntity.create(
            AGGREGATE_TYPE, // aggregateType
            matchId.toString(), // aggregateId
            "MATCH_CREATED", // eventType
            json, // payload
            matchCreatedTopic, // destinationTopic = "match-created"
            matchId.toString() // partitionKey = matchId
        );

        outboxEventRepository.save(outboxEventEntity);
    }

    /**
     * Saga 3단계 — 채널까지 준비된 매칭을 사용자에게 알린다.
     *
     * <p>알림을 여기서(백필 후에) 쏘는 게 핵심이다: channelId 가 확정된 뒤라 알림 하나로 채팅방을
     * 바로 열 수 있다. 채널 생성 시점에 쐈다면 클라이언트가 channelId 를 다시 물어봐야 한다.
     *
     * <p>fanout 은 디스패처가 envelope 기반이므로 payload 를 {@link FanoutEnvelope} 로 감싼다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeMatchFanout(UUID matchId, Long channelId, List<UUID> recipients) {
        MatchFanoutPayload payload = MatchFanoutPayload.of(matchId, channelId, recipients);
        FanoutEnvelope envelope = FanoutEnvelope.of(KafkaMessageType.MATCH_FANOUT.name(), payload);

        String json = jsonUtil.toJson(envelope).orElseThrow();

        OutboxEventEntity outboxEventEntity = OutboxEventEntity.create(
            AGGREGATE_TYPE,
            matchId.toString(),
            "MATCH_FANOUT",
            json,
            matchFanoutTopic,   // destinationTopic = "match-fanout"
            matchId.toString()
        );

        outboxEventRepository.save(outboxEventEntity);
    }

    /** 언매치 — message-service 가 이 이벤트로 DIRECT 채널을 CLOSED 로 닫는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeMatchUnmatched(UUID matchId, Long channelId, List<UUID> recipients) {
        MatchUnmatchedPayload payload = MatchUnmatchedPayload.of(matchId, channelId, recipients);

        String json = jsonUtil.toJson(payload).orElseThrow();

        OutboxEventEntity outboxEventEntity = OutboxEventEntity.create(
            AGGREGATE_TYPE,
            matchId.toString(),
            "MATCH_UNMATCHED",
            json,
            matchUnmatchedTopic,   // destinationTopic = "match-unmatched"
            matchId.toString()
        );

        outboxEventRepository.save(outboxEventEntity);
    }
}
