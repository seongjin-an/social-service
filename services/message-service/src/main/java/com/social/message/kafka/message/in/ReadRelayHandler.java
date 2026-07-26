package com.social.message.kafka.message.in;

import com.social.common.KeyPrefix;
import com.social.message.domain.ChannelMemberEntity;
import com.social.message.kafka.KafkaProducer;
import com.social.message.kafka.message.KafkaMessageProcessor;
import com.social.message.kafka.message.KafkaMessageType;
import com.social.message.kafka.message.out.ReadFanoutPayload;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import com.social.message.service.channelmember.ChannelMemberCacheService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Component
public class ReadRelayHandler implements KafkaMessageProcessor<ReadRelayRequest> {

    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberCacheService channelMemberCacheService;
    private final StringRedisTemplate redisTemplate;
    private final KafkaProducer kafkaProducer;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.READ_MESSAGE_RELAY;
    }

    @Override
    public Class<ReadRelayRequest> getPayloadType() {
        return ReadRelayRequest.class;
    }

    @Override
    public void handle(ReadRelayRequest message) {
        ChannelMemberEntity channelMember = channelMemberRepository
            .findByChannelIdAndUserId(message.channelId(), UUID.fromString(message.userId()))
            .orElseThrow();
        channelMember.updateLastReadMessageId(message.messageId());

        // 채널 입장 시 읽음 처리 → 오프라인 미읽음 카운터 제거
        redisTemplate.delete(KeyPrefix.UNREAD_COUNT + message.userId() + ":" + message.channelId());

        List<String> recipientIds = channelMemberCacheService.getRecipientIds(message.channelId());
        ReadFanoutPayload payload = new ReadFanoutPayload(
            message.channelId(), message.userId(), message.messageId(), recipientIds);

        kafkaProducer.sendReadFanout(payload);
    }
}
