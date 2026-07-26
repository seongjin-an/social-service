package com.social.profile.location;

import com.social.profile.repository.ProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 위치 갱신. {@code profile_location} UPSERT(백업) + Redis {@code geo:users} GEOADD(실시간 권위)를 함께 반영.
 *
 * <p>정합성: Redis 반영은 <b>DB 커밋 성공 후(afterCommit)</b>에만 실행한다.
 * → DB가 롤백되면 Redis에 유령 좌표가 남지 않는다. Redis 반영만 실패하면 DB엔 최신 좌표가 있으니
 *   다음 위치 핑에서 자연 복구된다(멱등).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UpdateLocationService {

    private final ProfileRepository profileRepository;
    private final ProfileLocationRepository profileLocationRepository;
    private final GeoRedisRepository geoRedisRepository;

    @Transactional
    public void updateLocation(String userId, String profileId, double lat, double lng) {
        UUID uid = UUID.fromString(userId);
        UUID pid = UUID.fromString(profileId);

        // 소유권 검증: 이 프로필이 이 유저의 것인지(아니면 조회 실패 → 거부).
        profileRepository.findByProfileIdAndUserId(pid, uid)
            .orElseThrow(() -> new IllegalArgumentException(
                "프로필을 찾을 수 없거나 권한이 없습니다: " + profileId));

        // DB UPSERT — 마지막 위치 백업(source of truth).
        ProfileLocationEntity location = profileLocationRepository.findById(pid)
            .map(existing -> {
                existing.updateCoordinates(lat, lng);
                return existing;
            })
            .orElseGet(() -> ProfileLocationEntity.of(pid, lat, lng));
        profileLocationRepository.save(location);

        // Redis GEOADD — 커밋 후에만 반영(멱등).
        registerGeoUpdateAfterCommit(pid, lng, lat);
    }

    private void registerGeoUpdateAfterCommit(UUID profileId, double lng, double lat) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    geoRedisRepository.updateUserLocation(profileId, lng, lat);
                } catch (Exception e) {
                    // Redis 반영 실패해도 DB엔 최신 위치가 있음 → 다음 위치 핑에서 복구.
                    log.warn("geo:users GEOADD 실패(다음 갱신에서 복구): profileId={}", profileId, e);
                }
            }
        });
    }
}
