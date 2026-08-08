package com.social.profile.location;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 실시간 반경검색용 Redis GEO 인덱스. 멤버 = <b>userId</b>, 좌표 = (lng, lat).
 * P2 추천의 {@code GEOSEARCH geo:users FROMMEMBER {userId} ...} 입력이 된다.
 *
 * <p><b>멤버가 userId 인 이유</b>: 위치는 사람 단위 속성이고, 반경검색 결과를 그대로
 * {@code profile:card:{userId}} MGET·좋아요·매칭에 이어 붙여야 한다. profileId 로 두면
 * 추천에서만 축을 바꾸는 매핑 조회가 영구히 따라붙는다.
 * 위치 백업 테이블({@code profile_location})은 프로필당 1:1 이라 PK 가 profileId 그대로다 —
 * 즉 <b>DB 는 프로필 단위, 반경검색 인덱스는 사람 단위</b>이고 변환은 {@link GeoCacheService} 가 한다.
 */
@Repository
public class GeoRedisRepository {

    private final String GEO_KEY;

    private final StringRedisTemplate redisTemplate;

    public GeoRedisRepository(@Value("${geo.key}") String GEO_KEY,
        @Value("${geo.duration}") Integer duration,
        StringRedisTemplate redisTemplate) {
        this.GEO_KEY = GEO_KEY;
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@code GEOADD geo:users {lng} {lat} {userId}} — 같은 멤버 재추가 시 좌표만 갱신(멱등).
     * Redis Point 는 (x=lng, y=lat) 순서다.
     */
    public void updateUserLocation(UUID userId, double lng, double lat) {
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), userId.toString());
    }

    /** ZREM geo:users {userId} — 이 유저를 반경검색 대상에서 제거. */
    public void remove(UUID userId) {
        redisTemplate.opsForZSet().remove(GEO_KEY, userId.toString());
    }
}
