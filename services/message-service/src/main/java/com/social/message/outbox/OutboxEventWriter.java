package com.social.message.outbox;

import com.social.common.ContentMessage;
import com.social.common.JsonUtil;
import com.social.message.kafka.message.KafkaInboundEnvelope;
import com.social.message.kafka.message.out.ChannelCreatedPayload;
import com.social.message.service.channelmember.ChannelMemberCacheService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ChannelMemberCacheService channelMemberCacheService;
    private final JsonUtil jsonUtil;

    @Value("${chatting.kafka.topics.message-fanout}")
    private String messageFanoutTopic;

    @Value("${chatting.kafka.topics.channel-created}")
    private String channelCreatedTopic;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(ContentMessage contentMessage) {
        List<String> recipientIds = channelMemberCacheService.getRecipientIds(
            contentMessage.channelId());

        OutboxPayload outboxPayload = OutboxPayload.of(contentMessage, recipientIds);
        KafkaInboundEnvelope envelope = new KafkaInboundEnvelope(
            "CONTENT_MESSAGE_FANOUT",
            jsonUtil.convertJsonNode(outboxPayload).orElseThrow()
        );
        String payload = jsonUtil.toJson(envelope)
            .orElseThrow(() -> new IllegalStateException("OutboxPayload 직렬화 실패"));

        OutboxEventEntity event = OutboxEventEntity.create(
            "MESSAGE",
            contentMessage.channelId() + ":" + contentMessage.messageId(),
            "MESSAGE_CREATED",
            payload,
            messageFanoutTopic,
            contentMessage.channelId().toString()
        );

        outboxEventRepository.save(event);
    }

    /**
     * Saga 2단계 — DIRECT 채널 생성 사실을 matching 에 알린다(channel_id 백필용).
     *
     * <p>채널/멤버 INSERT 와 <b>같은 트랜잭션</b>이어야 "채널은 만들었는데 알림은 안 나감"(또는 그 반대)이
     * 생기지 않는다. MANDATORY 로 트랜잭션 없이 호출되는 실수를 기동이 아니라 호출 시점에 즉시 터뜨린다.
     *
     * <p>payload 는 <b>raw JSON</b>(envelope 없음) — 소비자 matching 이 raw 로 파싱한다.
     * partition_key = matchId → 같은 매칭의 saga 이벤트는 항상 같은 파티션에서 순서가 보장된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeChannelCreated(UUID matchId, Long channelId, List<UUID> recipients) {
        ChannelCreatedPayload payload = ChannelCreatedPayload.of(matchId, channelId, recipients);
        String json = jsonUtil.toJson(payload)
            .orElseThrow(() -> new IllegalStateException("ChannelCreatedPayload 직렬화 실패"));

        OutboxEventEntity event = OutboxEventEntity.create(
            "CHANNEL",                // aggregateType
            channelId.toString(),     // aggregateId
            "CHANNEL_CREATED",        // eventType
            json,                     // payload (raw)
            channelCreatedTopic,      // destinationTopic = "channel-created"
            matchId.toString()        // partitionKey = saga 상관키
        );

        outboxEventRepository.save(event);
    }
}
