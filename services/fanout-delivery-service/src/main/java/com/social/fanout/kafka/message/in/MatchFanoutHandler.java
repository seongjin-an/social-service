package com.social.fanout.kafka.message.in;

import com.social.fanout.kafka.KafkaProducer;
import com.social.fanout.kafka.message.KafkaMessageProcessor;
import com.social.fanout.kafka.message.KafkaMessageType;
import com.social.fanout.routing.ConnectionRouteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 매칭 성사 알림을 두 당사자의 WS 세션으로 밀어 넣는다 — 메시지 fanout 과 같은 경로 해석을 쓴다.
 *
 * <p><b>오프라인은 그냥 넘어간다</b>: 매칭 사실은 이미 DB(matches)에 있고 사용자는 매칭 목록(F-M4)에서
 * 확인할 수 있다. 즉 알림 유실 ≠ 매칭 유실이므로, 여기서 별도 보관/재시도를 하지 않는다.
 * (메시지는 반대로 놓치면 안 되므로 미읽음 카운터를 올린다.)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MatchFanoutHandler implements KafkaMessageProcessor<MatchFanoutRequest> {

    private final KafkaProducer kafkaProducer;
    private final ConnectionRouteResolver connectionRouteResolver;

    @Override
    public KafkaMessageType getSupportedType() {
        return KafkaMessageType.MATCH_FANOUT;
    }

    @Override
    public Class<MatchFanoutRequest> getPayloadType() {
        return MatchFanoutRequest.class;
    }

    @Override
    public void handle(MatchFanoutRequest request) {
        for (String userId : request.recipientIds()) {
            var route = connectionRouteResolver.resolve(userId);
            if (route.instanceIds().isEmpty()) {
                log.info("[MatchFanout] 오프라인/세션없음 — 실시간 알림 스킵(목록에서 확인 가능): userId={}, matchId={}",
                    userId, request.matchId());
                continue;
            }
            route.instanceIds().forEach(instanceId -> kafkaProducer.sendMatchNotification(
                instanceId, userId, request.matchId(), request.channelId()));
        }
    }
}
