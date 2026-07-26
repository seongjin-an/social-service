package com.social.message.kafka.message.in;

import com.social.common.ContentMessage;
import com.social.message.domain.MessageEntity;
import com.social.message.kafka.message.KafkaMessageProcessor;
import com.social.message.kafka.message.KafkaMessageType;
import com.social.message.outbox.OutboxEventWriter;
import com.social.message.repository.message.MessageRepository;
import com.social.message.util.SnowflakeIdGenerator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class MessageRelayHandler implements KafkaMessageProcessor<MessageRelayRequest> {

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final MessageRepository messageRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.CONTENT_MESSAGE_RELAY;
    }

    @Override
    public Class<MessageRelayRequest> getPayloadType() {
        return MessageRelayRequest.class;
    }

    @Override
    @Transactional
    public void handle(MessageRelayRequest request) {
        // 0. 멱등성 검사 — 클라이언트 재전송/컨슈머 재처리(at-least-once) 시 중복 저장 방지.
        //    조회-후-저장 사이 race는 (channel_id, client_message_id) 유니크 제약이 최종 차단한다.
        if (request.clientMessageId() != null
            && messageRepository.existsByChannelIdAndClientMessageId(request.channelId(), request.clientMessageId())) {
            log.info("[Idempotency] duplicate message skipped: channelId={}, clientMessageId={}",
                request.channelId(), request.clientMessageId());
            return;
        }

        // 1. Snowflake ID 발급
        long messageId = snowflakeIdGenerator.nextId();
        long now = System.currentTimeMillis();

        // 2. 메시지 저장
        MessageEntity messageEntity = MessageEntity.of(
            request.channelId(),
            messageId,
            UUID.fromString(request.senderId()),
            request.senderName(),
            request.content(),
            request.clientMessageId()
        );
        messageRepository.save(messageEntity);

        // 3. Outbox 저장 (같은 트랜잭션 — 메시지 저장과 원자적으로 묶임)
        // DB는 Long, WS/API는 String으로 직렬화해 JS Number 정밀도 문제 방지
        ContentMessage contentMessage = ContentMessage.of(
            String.valueOf(messageId), request.channelId(), request.senderId(), request.senderName(),
            request.content(), now, request.clientMessageId()
        );
        outboxEventWriter.write(contentMessage);
    }
}
