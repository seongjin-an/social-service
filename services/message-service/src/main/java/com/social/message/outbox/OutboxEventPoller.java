package com.social.message.outbox;

import com.social.common.ContentMessage;
import com.social.common.JsonUtil;
import com.social.message.kafka.KafkaProducer;
import com.social.message.kafka.message.out.MessageFanoutPayload;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
//@Service
public class OutboxEventPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final KafkaProducer kafkaProducer;
    private final JsonUtil jsonUtil;

    @Scheduled(fixedDelay = 100)
    @Transactional
    public void poll() {
        List<OutboxEventEntity> events = outboxEventRepository.findPendingForUpdate();

        for (OutboxEventEntity event : events) {
            event.markInProgress();
        }

        for (OutboxEventEntity event : events) {
            try {
                ContentMessage contentMessage = jsonUtil.fromJson(event.getPayload(), ContentMessage.class)
                        .orElseThrow(() -> new IllegalStateException("payload 역직렬화 실패: " + event.getEventId()));

                List<String> recipientIds = channelMemberRepository
                        .findByChannelId(contentMessage.channelId())
                        .stream()
                        .map(member -> member.getUserId().toString())
                        .toList();

                MessageFanoutPayload payload = new MessageFanoutPayload(
                        event.getEventId().toString(),
                        contentMessage.channelId(),
                        contentMessage.messageId(),
                        contentMessage.senderId(),
                        contentMessage.senderName(),
                        contentMessage.content(),
                        MDC.get("trace_id"),
                        contentMessage.createdAt(),
                        recipientIds
                );

                kafkaProducer.sendMessageFanout(payload);
                event.markProcessed();
            } catch (Exception e) {
                log.error("[OutboxPoller] 처리 실패 eventId={}", event.getEventId(), e);
                event.markFailed(e.getMessage());
            }
        }
    }
}
