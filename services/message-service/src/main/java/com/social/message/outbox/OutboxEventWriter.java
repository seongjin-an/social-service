package com.social.message.outbox;

import com.social.common.ContentMessage;
import com.social.common.JsonUtil;
import com.social.message.kafka.message.KafkaInboundEnvelope;
import com.social.message.service.channelmember.ChannelMemberCacheService;
import java.util.List;
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
}
