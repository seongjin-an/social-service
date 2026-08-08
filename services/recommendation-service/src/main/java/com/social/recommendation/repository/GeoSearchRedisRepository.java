package com.social.recommendation.repository;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Repository;

/**
 * 반경검색 <b>읽기 전용</b> — {@code geo:users} 는 profile-service 소유다(여기서 절대 쓰지 않는다).
 * 멤버는 userId 이므로 결과를 곧바로 카드/seen/좋아요에 이어 붙일 수 있다.
 */
@Slf4j
@Repository
public class GeoSearchRedisRepository {

    private final String geoKey;
    private final StringRedisTemplate redisTemplate;

    public GeoSearchRedisRepository(
        @Value("${geo.key}") String geoKey,
        StringRedisTemplate redisTemplate
    ) {
        this.geoKey = geoKey;
        this.redisTemplate = redisTemplate;
    }

    /** 이 유저가 반경검색 인덱스에 있는지(=위치를 한 번이라도 올렸는지). */
    public boolean hasLocation(UUID userId) {
        List<Point> positions = redisTemplate.opsForGeo().position(geoKey, userId.toString());
        return positions != null && !positions.isEmpty() && positions.get(0) != null;
    }

    /**
     * {@code GEOSEARCH geo:users FROMMEMBER {userId} BYRADIUS {radiusKm} km ASC COUNT {limit} WITHDIST}
     *
     * <p>가까운 순으로 최대 {@code limit} 명. COUNT 로 자르는 이유는 뒤따르는 카드 MGET 이
     * 후보 수만큼 커지기 때문 — 반경이 넓은 유저 하나가 Redis 왕복을 통째로 키우는 걸 막는다.
     *
     * <p><b>호출 전 {@link #hasLocation(UUID)} 로 걸러야 한다</b> — FROMMEMBER 는 멤버가 없으면
     * 에러를 던진다(빈 결과가 아니다).
     *
     * @return 후보 (userId, 거리km) 목록. 자기 자신도 포함될 수 있으므로 호출 측이 제외한다.
     */
    public List<Candidate> searchNearby(UUID userId, double radiusKm, int limit) {
        GeoResults<GeoLocation<String>> results = redisTemplate.opsForGeo().search(
            geoKey,
            GeoReference.fromMember(userId.toString()),
            new Distance(radiusKm, Metrics.KILOMETERS),
            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(limit)
        );

        if (results == null) {
            return List.of();
        }

        return results.getContent().stream()
            .map(this::toCandidate)
            .filter(candidate -> candidate != null)
            .toList();
    }

    private Candidate toCandidate(GeoResult<GeoLocation<String>> result) {
        String member = result.getContent().getName();
        try {
            return new Candidate(UUID.fromString(member), result.getDistance().getValue());
        } catch (IllegalArgumentException e) {
            // UUID 가 아닌 멤버 = 예전 축(profileId)이나 손상된 값. 카드도 못 찾으므로 조용히 버린다.
            log.debug("geo:users 의 UUID 아닌 멤버 무시: {}", member);
            return null;
        }
    }

    /** 반경검색 결과 한 건 — 거리는 랭킹 점수와 응답(“3.4km”)에 함께 쓰인다. */
    public record Candidate(UUID userId, double distanceKm) {}
}
