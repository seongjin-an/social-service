package com.social.profile.repository;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 프로필별 관심사 태그 집합을 Redis SET 으로 유지 — {@code tags:{profileId}}.
 * P2 추천 랭킹의 겹침 계산({@code SINTERCARD tags:{me} tags:{cand}}) 입력이 된다.
 *
 * <p>멤버 토큰은 <b>tagId 문자열</b>(정규화 이름과 1:1). 표시명이 바뀌어도 집합이 안정적이고,
 * 추천 서비스도 동일하게 tagId 로 교집합을 계산하면 된다.
 *
 * <p><b>TTL</b>: 원본은 DB {@code profile_tag} 이고 이 SET 은 파생 캐시라 만료로 관리한다(메모리 회수·드리프트 자가치유).
 * 키 하나가 프로필 하나이므로 키 레벨 TTL 이 정확히 맞다. 다만 만료로 사라진 후보 집합은
 * <b>조회 측(P2)에서 rebuild-on-miss</b>(profile_tag 재적재)로 복구해야 SINTERCARD 가 0 으로 어긋나지 않는다.
 */
@Repository
public class TagRedisRepository {

    private final String keyPrefix;
    private final Duration ttl;
    private final StringRedisTemplate redisTemplate;

    public TagRedisRepository(
        @Value("${tags.key-prefix}") String keyPrefix,
        @Value("${tags.ttl}") Duration ttl,
        StringRedisTemplate redisTemplate
    ) {
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 프로필의 태그 집합을 통째로 교체(멱등): DEL 후 SADD + EXPIRE.
     * 태그가 없으면 키만 지운다(빈 집합 = SINTERCARD 0).
     */
    public void replaceTags(UUID profileId, Collection<String> tagTokens) {
        String key = keyPrefix + profileId;
        redisTemplate.delete(key);
        if (tagTokens.isEmpty()) {
            return;
        }
        redisTemplate.opsForSet().add(key, tagTokens.toArray(String[]::new));
        redisTemplate.expire(key, ttl);
    }

    /** DEL tags:{profileId} — 프로필 삭제 시 태그 집합 제거. */
    public void deleteTags(UUID profileId) {
        redisTemplate.delete(keyPrefix + profileId);
    }
}
