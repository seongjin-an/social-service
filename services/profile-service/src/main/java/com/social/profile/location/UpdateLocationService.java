package com.social.profile.location;

import com.social.profile.repository.ProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위치 갱신. {@code profile_location} UPSERT(백업) + Redis {@code geo:users} GEOADD(실시간 권위)를 함께 반영.
 *
 * <p>정합성: Redis 반영은 <b>DB 커밋 성공 후(afterCommit)</b>에만 실행한다({@link GeoCacheService}).
 * → DB가 롤백되면 Redis에 유령 좌표가 남지 않는다. Redis 반영만 실패하면 DB엔 최신 좌표가 있으니
 *   다음 위치 핑에서 자연 복구된다(멱등).
 *
 * <p>백업 테이블은 프로필 단위(PK=profileId), 반경검색 인덱스는 사람 단위(멤버=userId)다.
 * 멀티프로필 유저가 프로필을 번갈아 갱신하면 인덱스에는 <b>마지막으로 갱신한 좌표</b>가 남는다 —
 * 위치는 사람에 하나뿐인 속성이므로 의도된 동작이다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UpdateLocationService {

    private final ProfileRepository profileRepository;
    private final ProfileLocationRepository profileLocationRepository;
    private final GeoCacheService geoCacheService;

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

        // Redis GEOADD — 커밋 후에만 반영(멱등). 멤버는 userId.
        geoCacheService.refreshAfterCommit(uid, lat, lng);
    }
}
