package com.social.profile.location;

import com.social.profile.domain.ProfileEntity;
import com.social.profile.repository.ProfileRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 반경검색 인덱스({@code geo:users})를 <b>DB 커밋 성공 후에만</b> 갱신한다.
 * 태그/카드 캐시와 같은 afterCommit 패턴 — 롤백 시 유령 좌표가 남지 않는다.
 *
 * <p>인덱스는 <b>사람(userId) 단위</b>인데 위치 백업 테이블은 <b>프로필 단위</b>다. 이 축 변환이
 * 여기 모여 있다. 특히 프로필 삭제는 "이 유저를 빼야 하나"를 따져야 한다 — 남은 프로필이 있으면
 * 그 프로필의 좌표로 다시 세운다. 무조건 ZREM 하면 프로필 하나 지웠다고 멀티프로필 유저가
 * 반경검색에서 통째로 사라진다.
 *
 * <p>Redis 반영 실패는 삼킨다 — 인덱스는 파생 캐시이고 권위는 {@code profile_location} 이라
 * 다음 위치 핑에서 복구된다. 프로필 저장/삭제 자체를 실패시킬 이유가 없다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class GeoCacheService {

    private final ProfileRepository profileRepository;
    private final ProfileLocationRepository profileLocationRepository;
    private final GeoRedisRepository geoRedisRepository;

    /** 위치 갱신 반영 예약. 트랜잭션 안에서 호출해야 한다. */
    public void refreshAfterCommit(UUID userId, double lat, double lng) {
        afterCommit(() -> geoRedisRepository.updateUserLocation(userId, lng, lat),
            "geo:users GEOADD 실패", userId);
    }

    /**
     * 프로필 삭제 후 정리 — 남은 프로필의 좌표로 다시 세우고, 남은 게 없거나 좌표가 없으면 제거.
     *
     * <p>삭제 flush 이후에 호출돼야 지운 프로필이 후보에서 빠진다(파생 쿼리가 auto-flush 를 일으킨다).
     */
    public void refreshAfterProfileDeleted(UUID userId, UUID deletedProfileId) {
        Optional<ProfileEntity> remaining = profileRepository.findByUserId(userId).stream()
            .filter(profile -> !profile.getProfileId().equals(deletedProfileId))
            .findFirst();

        if (remaining.isEmpty()) {
            removeAfterCommit(userId);
            return;
        }

        Optional<ProfileLocationEntity> location =
            profileLocationRepository.findById(remaining.get().getProfileId());
        if (location.isEmpty()) {
            // 남은 프로필에 위치가 없다 → 반경검색 대상이 될 수 없으므로 인덱스에서 빠진다.
            removeAfterCommit(userId);
            return;
        }

        refreshAfterCommit(userId,
            location.get().getLat().doubleValue(),
            location.get().getLng().doubleValue());
    }

    /** 이 유저를 반경검색 대상에서 제거 예약. */
    public void removeAfterCommit(UUID userId) {
        afterCommit(() -> geoRedisRepository.remove(userId), "geo:users 제거 실패", userId);
    }

    //-------------------------------------------------------------------------------------------------
    // private
    //-------------------------------------------------------------------------------------------------

    private void afterCommit(Runnable action, String failMessage, UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 트랜잭션 밖에서 불린 경우 — 즉시 반영(테스트/배치 경로 방어).
            runQuietly(action, failMessage, userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runQuietly(action, failMessage, userId);
            }
        });
    }

    private void runQuietly(Runnable action, String failMessage, UUID userId) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("{}(다음 위치 갱신에서 복구): userId={}", failMessage, userId, e);
        }
    }
}
