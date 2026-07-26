package com.social.connection.service;

import com.social.common.JsonUtil;
import com.social.common.KeyPrefix;
import com.social.common.UserId;
import com.social.connection.dto.ConnectionInfo;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final JsonUtil jsonUtil;

    @Value("${websocket.connection.ttl-seconds}")
    private long ttlSeconds;

    public void saveConnectionInfo(UserId userId, String connectionKey, ConnectionInfo connectionInfo) {
        try {
            String userRedisKey = getUserRedisKey(userId);
            String connectionRedisKey = getConnectionRedisKey(connectionKey);

            redisTemplate.opsForSet().add(userRedisKey, connectionKey);
            redisTemplate.expire(userRedisKey, Duration.ofSeconds(ttlSeconds));

            String value = jsonUtil.toJson(connectionInfo).orElseThrow();
            redisTemplate.opsForValue().set(connectionRedisKey, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("error to save WebSocket session {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // 하트비트 수신 시 호출 — connection key, user Set 모두 TTL 갱신
    public void refreshConnectionTtl(UserId userId, String connectionKey) {
        redisTemplate.expire(getUserRedisKey(userId), Duration.ofSeconds(ttlSeconds));
        redisTemplate.expire(getConnectionRedisKey(connectionKey), Duration.ofSeconds(ttlSeconds));
    }

    public void removeConnectionInfo(UserId userId, String connectionKey) {
        String userRedisKey = getUserRedisKey(userId);
        String connectionRedisKey = getConnectionRedisKey(connectionKey);

        redisTemplate.opsForSet().remove(userRedisKey, connectionKey);
        redisTemplate.delete(connectionRedisKey);

        // 연결된 디바이스가 더 없으면 유저 키도 정리
        Long remaining = redisTemplate.opsForSet().size(userRedisKey);
        if (remaining != null && remaining == 0) {
            redisTemplate.delete(userRedisKey);
        }
    }

    private String getUserRedisKey(UserId userId) {
        return KeyPrefix.WEBSOCKET_USER + userId.id();
    }

    private String getConnectionRedisKey(String connectionKey) {
        return KeyPrefix.WEBSOCKET_CONNECTION + connectionKey;
    }
}
