package com.social.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 토큰 관리.
 *
 * 키 구조:
 *   RT:{userId}   → refreshToken 문자열  (TTL: refresh-token-expiration)
 *   BL:{jti}      → "1"                  (TTL: 해당 access token 의 남은 만료 시간)
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String REFRESH_PREFIX   = "RT:";
    private static final String BLACKLIST_PREFIX = "BL:";

    private final StringRedisTemplate redisTemplate;

    // ── Refresh Token ────────────────────────────────────────────────────────

    public void saveRefreshToken(String userId, String refreshToken, long ttlMs) {
        redisTemplate.opsForValue()
                .set(REFRESH_PREFIX + userId, refreshToken, Duration.ofMillis(ttlMs));
    }

    public String getRefreshToken(String userId) {
        return redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
    }

    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }

    // ── Blacklist ────────────────────────────────────────────────────────────

    /**
     * access token 의 jti 를 블랙리스트에 등록.
     * TTL 은 토큰의 남은 만료 시간으로 설정해 불필요한 메모리 점유를 방지한다.
     */
    public void blacklist(String jti, long remainingTtlMs) {
        if (remainingTtlMs <= 0) return;
        redisTemplate.opsForValue()
                .set(BLACKLIST_PREFIX + jti, "1", remainingTtlMs, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
