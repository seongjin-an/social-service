package com.social.service.outbox;

import com.social.common.JsonUtil;
import com.social.domain.outbox.OutboxEventEntity;
import com.social.kafka.message.out.MatchCreatedPayload;
import com.social.repository.outbox.OutboxEventRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutboxEventWriter {

    private final String matchCreatedTopic;
    private final JsonUtil jsonUtil;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventWriter(
        @Value("${social.kafka.topics.match-created}") String matchCreatedTopic,
        JsonUtil jsonUtil,
        OutboxEventRepository outboxEventRepository
    ) {
        this.matchCreatedTopic = matchCreatedTopic;
        this.jsonUtil = jsonUtil;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeMatchCreated(UUID matchId, UUID lo, UUID hi, List<UUID> recipients) {
        MatchCreatedPayload payload = MatchCreatedPayload.of(matchId, lo, hi, recipients);

        String json = jsonUtil.toJson(payload).orElseThrow();

        OutboxEventEntity outboxEventEntity = OutboxEventEntity.create(
            "MATCH", // aggregateType
            matchId.toString(), // aggregateId
            "MATCH_CREATED", // eventType
            json, // payload
            matchCreatedTopic, // destinationTopic = "match-created"
            matchId.toString() // partitionKey = matchId
        );

        outboxEventRepository.save(outboxEventEntity);
    }
}
