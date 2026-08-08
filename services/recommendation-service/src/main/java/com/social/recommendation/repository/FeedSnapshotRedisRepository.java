package com.social.recommendation.repository;

import com.social.common.KeyPrefix;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 추천 후보 스냅샷 — {@code feed:{userId}} LIST(랭킹 순서대로 앞이 상위) + {@code feed:ver:{userId}} 버전.
 * <b>recommendation 소유.</b>
 *
 * <p><b>왜 스냅샷인가</b>: 커서 페이지네이션의 기준이 필요하다. 매 요청마다 다시 계산하면 그 사이
 * 누군가 위치를 옮기거나 카드를 고쳐 점수가 흔들리고, 그러면 2페이지에서 같은 사람이 또 나오거나
 * 누군가 건너뛰어진다. 한 번 계산한 순서를 고정해 두고 그 안을 커서로 훑는다.
 * (F2-2 의 "미리 계산해 둔 feed 큐"와 같은 자산 — 콜드 계산 회피 효과도 같이 얻는다.)
 *
 * <p><b>버전 키</b>: TTL 만료 후 재계산되면 예전 커서의 offset 은 다른 목록을 가리킨다.
 * 커서에 버전을 실어 보내고 여기 값과 다르면 "무효"로 판정해 처음부터 다시 준다.
 *
 * <p>LIST 원소는 {@code {userId}:{거리km}} 다. 거리를 같이 담는 이유는 응답의 "3.4km" 를 위해
 * GEODIST 를 후보 수만큼 다시 때리지 않기 위함이다. userId(UUID)에는 {@code :} 가 없어 파싱이 안전하다.
 */
@Slf4j
@Repository
public class FeedSnapshotRedisRepository {

    private final Duration ttl;
    private final StringRedisTemplate redisTemplate;

    public FeedSnapshotRedisRepository(
        @Value("${feed.snapshot.ttl}") Duration ttl,
        StringRedisTemplate redisTemplate
    ) {
        this.ttl = ttl;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 스냅샷 교체 — 기존 목록을 지우고 랭킹 순서대로 다시 적재한다.
     *
     * @return 새 버전 토큰(커서에 실린다)
     */
    public String replace(UUID userId, List<FeedEntry> ranked) {
        String key = key(userId);
        String versionKey = versionKey(userId);
        String version = UUID.randomUUID().toString().substring(0, 8);

        redisTemplate.delete(key);
        if (!ranked.isEmpty()) {
            List<String> values = ranked.stream().map(FeedEntry::serialize).toList();
            redisTemplate.opsForList().rightPushAll(key, values);
            redisTemplate.expire(key, ttl);
        }
        redisTemplate.opsForValue().set(versionKey, version, ttl);

        return version;
    }

    /** 현재 스냅샷 버전 — 없으면 null(만료됐거나 아직 계산 전). */
    public String currentVersion(UUID userId) {
        return redisTemplate.opsForValue().get(versionKey(userId));
    }

    /** LRANGE — offset 부터 size 개. 범위를 넘으면 빈 목록. */
    public List<FeedEntry> page(UUID userId, int offset, int size) {
        List<String> values = redisTemplate.opsForList()
            .range(key(userId), offset, (long) offset + size - 1);
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(FeedEntry::parse)
            .filter(entry -> entry != null)
            .toList();
    }

    /** LLEN — 스냅샷 전체 후보 수(다음 커서가 필요한지 판단). */
    public long size(UUID userId) {
        Long size = redisTemplate.opsForList().size(key(userId));
        return size == null ? 0L : size;
    }

    private String key(UUID userId) {
        return KeyPrefix.FEED + userId;
    }

    private String versionKey(UUID userId) {
        return KeyPrefix.FEED + "ver:" + userId;
    }

    /** 스냅샷 한 칸. */
    public record FeedEntry(UUID userId, double distanceKm) {

        String serialize() {
            // Locale.ROOT — 소수점이 ',' 인 로케일에서 쓰면 다시 못 읽는다.
            return userId + ":" + String.format(Locale.ROOT, "%.2f", distanceKm);
        }

        static FeedEntry parse(String value) {
            int separator = value.lastIndexOf(':');
            if (separator < 0) {
                log.warn("스냅샷 원소 파싱 실패(무시): {}", value);
                return null;
            }
            try {
                return new FeedEntry(
                    UUID.fromString(value.substring(0, separator)),
                    Double.parseDouble(value.substring(separator + 1)));
            } catch (IllegalArgumentException e) {
                log.warn("스냅샷 원소 파싱 실패(무시): {}", value);
                return null;
            }
        }
    }
}
