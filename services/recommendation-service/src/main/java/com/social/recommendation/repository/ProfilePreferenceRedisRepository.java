package com.social.recommendation.repository;

import com.social.common.JsonUtil;
import com.social.common.KeyPrefix;
import com.social.common.ProfilePreference;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 선호값 캐시 읽기 전용 — {@code profile:pref:{userId}}. (쓰기는 profile-service 소유.)
 *
 * <p>미스면 {@link ProfilePreference#unrestricted(UUID)} 로 fallback 한다. 선호를 설정하지 않은 유저나
 * TTL 이 만료된 유저에게 <b>빈 피드를 주는 것보다 넓은 피드를 주는 게 낫다</b> — 필터는 편의 기능이고
 * 피드가 비는 건 기능 고장으로 보인다.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class ProfilePreferenceRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final JsonUtil jsonUtil;

    public ProfilePreference findByUserId(UUID userId) {
        String json = redisTemplate.opsForValue().get(KeyPrefix.PROFILE_PREF + userId);
        if (json == null) {
            log.debug("선호값 캐시 미스 — 제약 없음으로 진행: userId={}", userId);
            return ProfilePreference.unrestricted(userId);
        }
        return jsonUtil.fromJson(json, ProfilePreference.class)
            .orElseGet(() -> ProfilePreference.unrestricted(userId));
    }
}
