package com.social.profile.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.common.KeyPrefix;
import com.social.common.ProfileCard;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 프로필 카드 캐시 — {@code profile:card:{userId}} 에 카드 JSON 한 덩어리.
 *
 * <p><b>왜 캐시인가</b>: matching 의 매칭 목록/받은 좋아요는 상대 카드 N개를 한 번에 그려야 한다.
 * 서비스 간 REST 를 N번 때리는 대신 Redis MGET 한 번으로 끝낸다(매칭 목록 조회의 유일한 원격 호출).
 *
 * <p><b>키가 userId 인 이유</b>: 매칭·좋아요·채널이 전부 userId 기반이라 조회 측이 profileId 를 모른다.
 * 멀티프로필이라도 "그 사람의 대표 카드 1장"만 캐싱한다.
 *
 * <p><b>TTL</b>: 원본은 DB 라서 파생 캐시로 두고 만료시킨다(드리프트 자가 치유). 만료로 사라지면
 * 조회 측은 최소 정보(userId)만 있는 카드로 fallback 하고, 프로필이 한 번 수정되면 다시 채워진다.
 */
@Slf4j
@Repository
public class ProfileCardRedisRepository {

    private final Duration ttl;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProfileCardRedisRepository(
        @Value("${profile.card.ttl}") Duration ttl,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper
    ) {
        this.ttl = ttl;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** SET profile:card:{userId} = JSON, EX ttl (멱등 — 통째로 덮어쓴다). */
    public void save(ProfileCard card) {
        try {
            String json = objectMapper.writeValueAsString(card);
            redisTemplate.opsForValue().set(key(card.userId()), json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("프로필 카드 직렬화 실패(캐시 스킵): userId={}", card.userId(), e);
        }
    }

    /** DEL profile:card:{userId} — 프로필이 전부 사라졌을 때. */
    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return KeyPrefix.PROFILE_CARD + userId;
    }
}
