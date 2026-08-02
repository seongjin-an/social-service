package com.social.kafka;

import com.social.common.JsonUtil;
import com.social.kafka.message.out.LikeRelayPayload;
import com.social.service.like.LikeJudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaConsumer {

    private final JsonUtil jsonUtil;
    private final LikeJudgeService likeJudgeService;

    @KafkaListener(
        topics = "${social.kafka.listeners.like-relay.topic}",
        groupId = "${social.kafka.listeners.like-relay.group}",
        concurrency = "${social.kafka.listeners.like-relay.concurrency}"
    )
    public void consumeLikeRelay(ConsumerRecord<String, String> record, Acknowledgment ack) {
        LikeRelayPayload payload = jsonUtil.fromJson(record.value(), LikeRelayPayload.class)
            .orElseThrow();

        likeJudgeService.judge(payload);
        ack.acknowledge();
    }
}
