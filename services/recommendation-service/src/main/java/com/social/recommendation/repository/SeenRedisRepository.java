package com.social.recommendation.repository;

import com.social.common.KeyPrefix;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 이미 본(=좋아요/패스한, 또는 노출된) 상대 집합 — {@code seen:{userId}} SET. <b>recommendation 소유.</b>
 *
 * <p>SADD 라 재처리·중복 요청에 안전하다(멱등). 원본 테이블이 없는 데이터라 TTL 은 넉넉히 두지만,
 * 무한히 두면 지워지지 않는 키가 유저마다 쌓이므로 만료는 둔다.
 */
@Slf4j
@Repository
public class SeenRedisRepository {

    private final Duration ttl;
    private final StringRedisTemplate redisTemplate;

    public SeenRedisRepository(
        @Value("${seen.ttl}") Duration ttl,
        StringRedisTemplate redisTemplate
    ) {
        this.ttl = ttl;
        this.redisTemplate = redisTemplate;
    }

    /** SADD seen:{userId} + TTL 갱신(슬라이딩) — 활동하는 유저의 기록이 만료로 사라지지 않게. */
    public void add(UUID userId, List<UUID> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        String key = KeyPrefix.SEEN + userId;
        String[] members = targetIds.stream().map(UUID::toString).toArray(String[]::new);

        redisTemplate.opsForSet().add(key, members);
        redisTemplate.expire(key, ttl);
    }

    /**
     * 후보 중 이미 본 사람만 골라낸다 — SMISMEMBER 한 번(후보 수만큼 왕복하지 않는다).
     * SMEMBERS 로 전량을 끌어오지 않는 이유: 오래 쓴 유저의 seen 은 수천 건까지 자란다.
     */
    public Set<UUID> filterSeen(UUID userId, List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Set.of();
        }
        String key = KeyPrefix.SEEN + userId;
        Object[] members = candidateIds.stream().map(UUID::toString).toArray();

        Map<Object, Boolean> hits = redisTemplate.opsForSet().isMember(key, members);
        if (hits == null) {
            return Set.of();
        }

        return hits.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(entry -> UUID.fromString((String) entry.getKey()))
            .collect(Collectors.toSet());
    }
}
