package com.social.recommendation.repository;

import com.social.common.JsonUtil;
import com.social.common.KeyPrefix;
import com.social.common.ProfileCard;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 프로필 카드 캐시 읽기 전용 — {@code profile:card:{userId}} 를 <b>MGET 한 번</b>으로 배치 조회한다.
 * (쓰기는 profile-service 소유.)
 *
 * <p>matching 의 같은 이름 클래스와 달리 <b>미스를 minimal 로 채우지 않고 아예 제외</b>한다.
 * 매칭 목록은 "매칭은 있는데 카드가 없는" 사람도 보여줘야 하지만, 추천 피드에서 이름도 나이도
 * 사진도 없는 카드를 스와이프하게 만들 이유가 없다. 필터 입력(나이·성별)도 없어서 걸러낼 수도 없다.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class ProfileCardRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final JsonUtil jsonUtil;

    /** userId → 카드. <b>캐시에 없는 userId 는 결과에서 빠진다</b>(입력 순서는 보존). */
    public Map<UUID, ProfileCard> findAllByUserIds(List<UUID> userIds) {
        Map<UUID, ProfileCard> result = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }

        List<UUID> distinct = userIds.stream().distinct().toList();
        List<String> keys = distinct.stream().map(id -> KeyPrefix.PROFILE_CARD + id).toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return result;
        }

        for (int i = 0; i < distinct.size(); i++) {
            String json = i < values.size() ? values.get(i) : null;
            if (json == null) {
                continue;
            }
            UUID userId = distinct.get(i);
            jsonUtil.fromJson(json, ProfileCard.class)
                .ifPresent(card -> result.put(userId, card));
        }
        return result;
    }

    /** 요청자 본인 카드 — 태그 겹침 점수의 기준이 된다. 없으면 empty. */
    public Optional<ProfileCard> findByUserId(UUID userId) {
        String json = redisTemplate.opsForValue().get(KeyPrefix.PROFILE_CARD + userId);
        return json == null ? Optional.empty() : jsonUtil.fromJson(json, ProfileCard.class);
    }
}
