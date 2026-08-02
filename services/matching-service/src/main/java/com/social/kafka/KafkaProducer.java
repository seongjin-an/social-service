package com.social.kafka;

import com.social.common.JsonUtil;
import com.social.common.StringUtils;
import com.social.kafka.message.out.LikeRelayPayload;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaProducer {

    private final String LIKE_RELAY_TYPE = "LIKE_RELAY_TYPE";

    private final String likeRelayTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonUtil jsonUtil;


    public KafkaProducer(
        @Value("${social.kafka.topics.like-relay}") String likeRelayTopic,
        KafkaTemplate<String, String> kafkaTemplate,
        JsonUtil jsonUtil
    ) {
        this.likeRelayTopic = likeRelayTopic;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonUtil = jsonUtil;
    }

    public void publishLikeRelay(LikeRelayPayload payload) {
        UUID userLoId = StringUtils.minUuid(payload.fromUserId(), payload.toUserId());
        UUID userHiId = StringUtils.maxUuid(payload.fromUserId(), payload.toUserId());
        String key = "%s:%s".formatted(userLoId.toString(), userHiId.toString());

        KafkaEnvelope envelope = new KafkaEnvelope(
            LIKE_RELAY_TYPE,
            jsonUtil.convertJsonNode(payload).orElseThrow()
        );
        String json = jsonUtil.toJson(envelope).orElseThrow();
        kafkaTemplate.send(likeRelayTopic, key, json);
    }
}
