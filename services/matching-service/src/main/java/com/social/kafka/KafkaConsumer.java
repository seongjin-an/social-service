package com.social.kafka;

import com.social.common.JsonUtil;
import com.social.kafka.message.in.ChannelCreatedRequest;
import com.social.kafka.message.out.LikeRelayPayload;
import com.social.service.like.LikeJudgeService;
import com.social.service.match.MatchSagaService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 두 리스너 모두 <b>못 읽는 레코드는 건너뛰고(ack), 처리 실패는 재배달에 맡긴다</b>.
 * 형식이 깨진 레코드는 몇 번 재시도해도 성공하지 않으므로 파티션을 붙잡아 두면 손해만 크다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaConsumer {

    private final JsonUtil jsonUtil;
    private final LikeJudgeService likeJudgeService;
    private final MatchSagaService matchSagaService;

    @KafkaListener(
        topics = "${social.kafka.listeners.like-relay.topic}",
        groupId = "${social.kafka.listeners.like-relay.group}",
        concurrency = "${social.kafka.listeners.like-relay.concurrency}"
    )
    public void consumeLikeRelay(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Optional<LikeRelayPayload> parsed = jsonUtil.fromJson(record.value(), LikeRelayPayload.class)
            .filter(payload -> payload.fromUserId() != null
                && payload.toUserId() != null
                && payload.type() != null);

        if (parsed.isEmpty()) {
            log.warn("[Judge] like-relay 처리 불가 — 건너뜀: offset={}, value={}",
                record.offset(), record.value());
            ack.acknowledge();
            return;
        }

        likeJudgeService.judge(parsed.get());
        ack.acknowledge();
    }

    /**
     * Saga 3단계 — message-service 가 DIRECT 채널을 만들었다는 통보. channel_id 를 백필하고 알림을 쏜다.
     * 처리 실패 시 ack 하지 않아 재배달되고, 핸들러가 멱등하므로 결국 백필된다.
     */
    @KafkaListener(
        topics = "${social.kafka.listeners.channel-created.topic}",
        groupId = "${social.kafka.listeners.channel-created.group}",
        concurrency = "${social.kafka.listeners.channel-created.concurrency}"
    )
    public void consumeChannelCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Optional<ChannelCreatedRequest> parsed =
            jsonUtil.fromJson(record.value(), ChannelCreatedRequest.class)
                .filter(request -> request.matchId() != null && request.channelId() != null);

        if (parsed.isEmpty()) {
            log.warn("[Saga] channel-created 처리 불가 — 건너뜀: offset={}, value={}",
                record.offset(), record.value());
            ack.acknowledge();
            return;
        }

        matchSagaService.backfillChannel(parsed.get());
        ack.acknowledge();
    }
}
