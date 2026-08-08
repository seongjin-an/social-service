package com.social.repository.card;

import com.social.common.JsonUtil;
import com.social.common.KeyPrefix;
import com.social.common.ProfileCard;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 프로필 카드 캐시 읽기 전용 — {@code profile:card:{userId}} 를 <b>MGET 한 번</b>으로 배치 조회한다.
 * (쓰기는 profile-service 소유. matching 은 절대 쓰지 않는다.)
 *
 * <p>매칭 목록이 N명이어도 원격 호출은 1회다. profile-service 에 REST 를 N번 때리는 대신 이걸 쓰는 게
 * F-M4 의 설계 포인트 — 조회 경로에 서비스 간 동기 의존이 없다.
 *
 * <p><b>캐시 미스</b>(TTL 만료 / 프로필 미작성)는 {@link ProfileCard#minimal(UUID)} 로 채운다.
 * 매칭은 존재하는데 카드만 없는 상황이 목록 전체를 깨뜨리면 안 된다.
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class ProfileCardRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final JsonUtil jsonUtil;

    /** userId → 카드. 순서는 입력 순서를 보존하고, 미스는 minimal 카드로 채워 항상 전부 반환한다. */
    public Map<UUID, ProfileCard> findAllByUserIds(List<UUID> userIds) {
        Map<UUID, ProfileCard> result = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }

        List<UUID> distinct = userIds.stream().distinct().toList();
        List<String> keys = distinct.stream().map(id -> KeyPrefix.PROFILE_CARD + id).toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        for (int i = 0; i < distinct.size(); i++) {
            UUID userId = distinct.get(i);
            String json = (values == null || i >= values.size()) ? null : values.get(i);

            ProfileCard card = (json == null)
                ? ProfileCard.minimal(userId)
                : jsonUtil.fromJson(json, ProfileCard.class).orElseGet(() -> ProfileCard.minimal(userId));

            result.put(userId, card);
        }
        return result;
    }
}
