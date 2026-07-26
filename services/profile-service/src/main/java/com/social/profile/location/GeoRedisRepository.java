package com.social.profile.location;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 실시간 반경검색용 Redis GEO 인덱스. 멤버 = profileId, 좌표 = (lng, lat). P2 추천의 {@code GEOSEARCH geo:users ...}
 * 입력이 된다.
 */
@Repository
public class GeoRedisRepository {

    private final String GEO_KEY;
    //private final Integer duration;

    private final StringRedisTemplate redisTemplate;

    public GeoRedisRepository(@Value("${geo.key}") String GEO_KEY,
        @Value("${geo.duration}") Integer duration,
        StringRedisTemplate redisTemplate) {
        this.GEO_KEY = GEO_KEY;
        //this.duration = duration;
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@code GEOADD geo:users {lng} {lat} {profileId}} — 같은 멤버 재추가 시 좌표만 갱신(멱등). Redis Point 는
     * (x=lng, y=lat) 순서다.
     */
    public void updateUserLocation(UUID profileId, double lng, double lat) {
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), profileId.toString());
    }
}
