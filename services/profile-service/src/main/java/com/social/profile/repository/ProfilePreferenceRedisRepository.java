package com.social.profile.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.common.KeyPrefix;
import com.social.common.ProfilePreference;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 추천 선호값 캐시 — {@code profile:pref:{userId}} 에 JSON 한 덩어리.
 *
 * <p>카드 캐시와 같은 구조/같은 이유(파생 캐시 + TTL 자가 치유). 다만 읽는 쪽이 <b>본인 것 1건</b>만
 * 보므로 MGET 이 아니라 GET 이고, 미스 시 소비자는 "제약 없음"으로 fallback 한다.
 */
@Slf4j
@Repository
public class ProfilePreferenceRedisRepository {

    private final Duration ttl;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProfilePreferenceRedisRepository(
        @Value("${profile.pref.ttl}") Duration ttl,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper
    ) {
        this.ttl = ttl;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** SET profile:pref:{userId} = JSON, EX ttl (멱등 — 통째로 덮어쓴다). */
    public void save(ProfilePreference preference) {
        try {
            String json = objectMapper.writeValueAsString(preference);
            redisTemplate.opsForValue().set(key(preference.userId()), json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("선호값 직렬화 실패(캐시 스킵): userId={}", preference.userId(), e);
        }
    }

    /** DEL profile:pref:{userId} — 프로필이 전부 사라졌을 때. */
    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return KeyPrefix.PROFILE_PREF + userId;
    }
}
