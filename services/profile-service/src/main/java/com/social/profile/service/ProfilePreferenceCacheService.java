package com.social.profile.service;

import com.social.common.ProfilePreference;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.repository.ProfilePreferenceRedisRepository;
import com.social.profile.repository.ProfileRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 추천 선호값 캐시({@code profile:pref:{userId}})를 <b>DB 커밋 성공 후에만</b> 갱신한다.
 * 카드/태그/geo 캐시와 같은 afterCommit 패턴.
 *
 * <p>갱신 시점은 <b>프로필 생성·수정·삭제</b>뿐이다 — 이미지 변경은 선호값과 무관하므로 훅을 걸지 않는다.
 *
 * <p>카드와 마찬가지로 조립은 트랜잭션 안에서, Redis 쓰기만 커밋 후로 미룬다.
 * 반영 실패는 삼킨다(파생 캐시 — 소비자가 "제약 없음"으로 fallback 하고 다음 수정에서 복구된다).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProfilePreferenceCacheService {

    private final ProfileRepository profileRepository;
    private final ProfilePreferenceRedisRepository profilePreferenceRedisRepository;

    /** 이 프로필의 선호값으로 갱신 예약. 트랜잭션 안에서 호출해야 한다. */
    public void refreshAfterCommit(UUID userId, UUID profileId) {
        ProfilePreference preference = assemble(userId, profileId);
        if (preference == null) {
            return;
        }
        afterCommit(() -> profilePreferenceRedisRepository.save(preference), "선호값 캐시 반영 실패", userId);
    }

    /**
     * 프로필 삭제 후 정리 — 남은 프로필이 있으면 그 선호값으로 다시 세우고, 없으면 제거.
     * (카드와 동일한 "남은 것 중 첫 프로필을 대표로" 규칙 — 활성 프로필 개념이 아직 없다.)
     */
    public void refreshAfterProfileDeleted(UUID userId, UUID deletedProfileId) {
        Optional<ProfileEntity> remaining = profileRepository.findByUserId(userId).stream()
            .filter(profile -> !profile.getProfileId().equals(deletedProfileId))
            .findFirst();

        if (remaining.isEmpty()) {
            afterCommit(() -> profilePreferenceRedisRepository.delete(userId), "선호값 캐시 제거 실패", userId);
            return;
        }
        refreshAfterCommit(userId, remaining.get().getProfileId());
    }

    //-------------------------------------------------------------------------------------------------
    // private
    //-------------------------------------------------------------------------------------------------

    private ProfilePreference assemble(UUID userId, UUID profileId) {
        Optional<ProfileEntity> found = profileRepository.findById(profileId);
        if (found.isEmpty()) {
            log.warn("선호값 조립 스킵 — 프로필 없음: profileId={}", profileId);
            return null;
        }
        ProfileEntity profile = found.get();

        return ProfilePreference.of(
            userId,
            profile.getPrefGender() == null ? null : profile.getPrefGender().name(),
            profile.getPrefAgeMin(),
            profile.getPrefAgeMax(),
            profile.getPrefDistanceKm()
        );
    }

    private void afterCommit(Runnable action, String failMessage, UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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
            log.warn("{}(다음 프로필 수정에서 복구): userId={}", failMessage, userId, e);
        }
    }
}
