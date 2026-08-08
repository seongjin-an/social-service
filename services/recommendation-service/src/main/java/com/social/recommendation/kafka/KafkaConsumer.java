package com.social.recommendation.kafka;

import com.social.common.JsonUtil;
import com.social.recommendation.kafka.message.in.LikeRelayRequest;
import com.social.recommendation.service.SeenService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * {@code like-relay} 구독 — 좋아요/패스한 상대를 seen 에 적재한다(F2-3).
 *
 * <p><b>왜 이벤트인가</b>: matching 이 이미 발행하는 이벤트에 필요한 정보가 다 있다. 별도 API 를
 * 만들어 클라이언트에게 두 번 호출하게 하거나 matching 이 남의 서비스 키(seen)를 직접 쓰게 하는 것보다
 * 낫다. 컨슈머 그룹만 다르므로 matching 의 판정 컨슈머와 서로 간섭하지 않는다.
 *
 * <p><b>부수 효과 하나가 공짜로 따라온다</b>: 매칭은 서로 좋아요를 눌러야 성립하므로, 매칭된 상대는
 * 반드시 내 seen 에 들어 있다 → "이미 매칭된 상대 제외"를 위한 별도 조회가 필요 없다.
 *
 * <p>못 읽는 레코드는 ack 후 건너뛴다(재시도해도 성공하지 않는다). 처리 실패는 재배달에 맡기고,
 * SADD 가 멱등이므로 재처리가 안전하다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaConsumer {

    private final JsonUtil jsonUtil;
    private final SeenService seenService;

    @KafkaListener(
        topics = "${social.kafka.listeners.like-relay.topic}",
        groupId = "${social.kafka.listeners.like-relay.group}",
        concurrency = "${social.kafka.listeners.like-relay.concurrency}"
    )
    public void consumeLikeRelay(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Optional<LikeRelayRequest> parsed = jsonUtil.fromJson(record.value(), LikeRelayRequest.class)
            .filter(request -> request.fromUserId() != null && request.toUserId() != null);

        if (parsed.isEmpty()) {
            log.warn("[Seen] like-relay 처리 불가 — 건너뜀: offset={}, value={}",
                record.offset(), record.value());
            ack.acknowledge();
            return;
        }

        LikeRelayRequest request = parsed.get();
        seenService.markSeen(request.fromUserId(), List.of(request.toUserId()));
        ack.acknowledge();
    }
}
