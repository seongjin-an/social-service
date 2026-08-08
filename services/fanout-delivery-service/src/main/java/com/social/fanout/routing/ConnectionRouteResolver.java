package com.social.fanout.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.social.common.JsonUtil;
import com.social.common.KeyPrefix;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * "이 유저는 어느 connection-service 인스턴스에 붙어 있나"를 Redis 에서 푼다.
 * 메시지·읽음·매칭 알림이 모두 같은 방식으로 라우팅되므로 여기 한 곳에 모았다.
 *
 * <pre>
 * ws:user:{userId}            → SET of connectionKey
 * ws:connection:{connectionKey} → JSON { instanceId, ... }   (TTL 있음)
 * </pre>
 *
 * <p>연결 정보가 TTL 로 만료된 stale connectionKey 는 조회 중에 SET 에서 제거한다(자가 정리).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ConnectionRouteResolver {

    private final StringRedisTemplate redisTemplate;
    private final JsonUtil jsonUtil;

    public ConnectionRoute resolve(String userId) {
        String userKey = KeyPrefix.WEBSOCKET_USER + userId;
        Set<String> connectionKeys = redisTemplate.opsForSet().members(userKey);
        if (connectionKeys == null || connectionKeys.isEmpty()) {
            return ConnectionRoute.offline();
        }

        Set<String> instanceIds = new HashSet<>();
        for (String connectionKey : connectionKeys) {
            String connectionInfoJson = redisTemplate.opsForValue()
                .get(KeyPrefix.WEBSOCKET_CONNECTION + connectionKey);
            if (connectionInfoJson == null) {
                redisTemplate.opsForSet().remove(userKey, connectionKey);
                log.debug("[Fanout] Removed stale connectionKey={} for userId={}", connectionKey, userId);
                continue;
            }
            jsonUtil.fromJson(connectionInfoJson, JsonNode.class)
                .map(node -> node.get("instanceId"))
                .map(JsonNode::asText)
                .ifPresent(instanceIds::add);
        }

        return new ConnectionRoute(true, instanceIds);
    }
}
