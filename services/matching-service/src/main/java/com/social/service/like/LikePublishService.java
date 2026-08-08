package com.social.service.like;

import com.social.common.StringUtils;
import com.social.common.exception.BusinessException;
import com.social.domain.like.LikeType;
import com.social.kafka.KafkaProducer;
import com.social.kafka.message.out.LikeRelayPayload;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class LikePublishService {

    private final KafkaProducer kafkaProducer;

    public void publishLikeRelay(String from, String to, LikeType type) {
        UUID fromUserId = StringUtils.fromUuid(from);
        UUID toUserId = StringUtils.fromUuid(to);

        // 자기 자신에게 좋아요 금지 → 400
        if (fromUserId.equals(toUserId)) {
            throw BusinessException.badRequest("자기 자신에게는 좋아요를 보낼 수 없습니다");
        }

        LikeRelayPayload likeRelayPayload = LikeRelayPayload.of(fromUserId, toUserId, type);
        kafkaProducer.publishLikeRelay(likeRelayPayload);
    }
}
