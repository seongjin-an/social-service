package com.social.message.kafka.message.in;

import com.social.common.ContentMessage;
import com.social.message.domain.ChannelStatus;
import com.social.message.domain.MessageEntity;
import com.social.message.kafka.message.KafkaMessageProcessor;
import com.social.message.kafka.message.KafkaMessageType;
import com.social.message.outbox.OutboxEventWriter;
import com.social.message.repository.channel.ChannelRepository;
import com.social.message.repository.message.MessageRepository;
import com.social.message.service.channelmember.ChannelMemberCacheService;
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
    private final ChannelRepository channelRepository;
    private final ChannelMemberCacheService channelMemberCacheService;
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
        // 0-A. CLOSED 채널 전송 가드 — 언매치로 닫힌 방에는 신규 메시지를 저장/발행하지 않는다.
        //      status 컬럼은 나중에 추가됐으므로 기존 행은 NULL → "CLOSED 일 때만" 거부한다.
        if (channelRepository.findStatusByChannelId(request.channelId())
            .filter(ChannelStatus.CLOSED::equals).isPresent()) {
            log.info("[ClosedChannel] 전송 거부: channelId={}, senderId={}",
                request.channelId(), request.senderId());
            return;
        }

        // 0-A2. 멤버십 가드 — 발신자가 그 방의 멤버가 아니면 저장/발행하지 않는다.
        //      오픈채팅(P3)이 생기면서 필요해졌다: 방 목록이 공개돼 channelId 를 누구나 알 수 있으므로,
        //      가드가 없으면 입장하지 않은 사람이 WS 로 channelId 만 바꿔 아무 방에나 글을 쓸 수 있다.
        //      (DIRECT 채널도 같은 구멍이 있었다 — 남의 1:1 대화방에 끼어들기.)
        //      비용은 0 이다: 바로 아래 OutboxEventWriter 가 읽을 캐시를 여기서 먼저 읽을 뿐이다.
        if (!isMember(request.channelId(), request.senderId())) {
            log.warn("[NotAMember] 전송 거부: channelId={}, senderId={}",
                request.channelId(), request.senderId());
            return;
        }

        // 0-B. 멱등성 검사 — 클라이언트 재전송/컨슈머 재처리(at-least-once) 시 중복 저장 방지.
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

    /**
     * {@code channel:members:{channelId}} SET 으로 판정한다(miss 면 DB 에서 재구성).
     * UUID 대소문자가 경로마다 다를 수 있어 대소문자 무시로 비교한다.
     */
    private boolean isMember(Long channelId, String senderId) {
        return channelMemberCacheService.getRecipientIds(channelId).stream()
            .anyMatch(memberId -> memberId.equalsIgnoreCase(senderId));
    }
}
