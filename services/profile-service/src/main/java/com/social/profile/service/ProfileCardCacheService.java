package com.social.profile.service;

import com.social.common.ProfileCard;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileImageEntity;
import com.social.profile.repository.ProfileCardRedisRepository;
import com.social.profile.repository.ProfileImageRepository;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 프로필 카드 캐시({@code profile:card:{userId}})를 <b>DB 커밋 성공 후에만</b> 갱신한다.
 * 태그/geo 캐시와 같은 afterCommit 패턴 — 롤백 시 유령 카드가 남지 않는다.
 *
 * <p><b>카드 조립은 트랜잭션 안에서</b> 하고, Redis 쓰기만 커밋 후로 미룬다.
 * afterCommit 콜백에서 DB 를 읽으면 이미 종료된 트랜잭션 자원에 손대게 되므로 피한다.
 *
 * <p>Redis 반영 실패는 삼킨다 — 카드는 파생 캐시이고, 다음 프로필 수정이나 TTL 만료 후
 * 조회 측 fallback 으로 복구된다. 프로필 저장 자체를 실패시킬 이유가 없다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProfileCardCacheService {

    private final ProfileRepository profileRepository;
    private final ProfileTagRepository profileTagRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileCardRedisRepository profileCardRedisRepository;

    /**
     * 이 프로필 내용으로 유저의 카드를 갱신 예약. 트랜잭션 안에서 호출해야 한다.
     * (프로필 저장/수정, 이미지 업로드·삭제·대표변경·순서변경 등 카드 표시가 바뀌는 모든 경로)
     */
    public void refreshAfterCommit(UUID userId, UUID profileId) {
        ProfileCard card = assemble(userId, profileId);
        if (card == null) {
            return;
        }
        afterCommit(() -> profileCardRedisRepository.save(card), "카드 캐시 반영 실패", userId);
    }

    /** 유저의 카드 제거 예약 — 마지막 프로필이 삭제된 경우. */
    public void deleteAfterCommit(UUID userId) {
        afterCommit(() -> profileCardRedisRepository.delete(userId), "카드 캐시 제거 실패", userId);
    }

    /**
     * 프로필 삭제 후 정리 — 남은 프로필이 있으면 그걸로 카드를 다시 세우고, 없으면 카드를 지운다.
     * (멀티프로필에 "활성 프로필" 개념이 아직 없어 남은 것 중 첫 프로필을 대표로 쓴다.)
     *
     * <p>삭제 flush 이후에 호출돼야 지운 프로필이 후보에서 빠진다 — JPQL/파생 쿼리가 auto-flush 를
     * 일으키므로 {@code delete()} 뒤에서 호출하면 된다.
     */
    public void refreshAfterProfileDeleted(UUID userId, UUID deletedProfileId) {
        Optional<ProfileEntity> remaining = profileRepository.findByUserId(userId).stream()
            .filter(p -> !p.getProfileId().equals(deletedProfileId))
            .findFirst();

        if (remaining.isEmpty()) {
            deleteAfterCommit(userId);
            return;
        }
        refreshAfterCommit(userId, remaining.get().getProfileId());
    }

    //-------------------------------------------------------------------------------------------------
    // private
    //-------------------------------------------------------------------------------------------------

    /** 트랜잭션 안에서 카드를 조립한다(태그·대표 이미지 포함). 프로필이 없으면 null. */
    private ProfileCard assemble(UUID userId, UUID profileId) {
        Optional<ProfileEntity> found = profileRepository.findById(profileId);
        if (found.isEmpty()) {
            log.warn("카드 조립 스킵 — 프로필 없음: profileId={}", profileId);
            return null;
        }
        ProfileEntity profile = found.get();

        List<String> tags = profileTagRepository.findByProfileId(profileId).stream()
            .map(pt -> pt.getTag().getName())
            .toList();

        return ProfileCard.of(
            userId,
            profileId,
            calculateAge(profile.getBirthday()),
            profile.getGender() == null ? null : profile.getGender().name(),
            profile.getBio(),
            tags,
            resolveCardImageUrl(profileId)
        );
    }

    /** 대표 이미지 → 없으면 정렬 첫 장 → 없으면 null(사진 없는 프로필). */
    private String resolveCardImageUrl(UUID profileId) {
        return profileImageRepository.findPrimaryByProfileId(profileId)
            .or(() -> profileImageRepository.findByProfileIdOrderBySortOrder(profileId).stream().findFirst())
            .map(ProfileImageEntity::getImageUrl)
            .orElse(null);
    }

    private Integer calculateAge(LocalDate birthday) {
        return birthday == null ? null : Period.between(birthday, LocalDate.now()).getYears();
    }

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
            log.warn("{}(다음 갱신에서 복구): userId={}", failMessage, userId, e);
        }
    }
}
