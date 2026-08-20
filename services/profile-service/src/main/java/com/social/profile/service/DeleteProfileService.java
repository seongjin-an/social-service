package com.social.profile.service;

import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileImageEntity;
import com.social.profile.domain.ProfileTagEntity;
import com.social.profile.location.GeoCacheService;
import com.social.profile.location.ProfileLocationRepository;
import com.social.profile.repository.ProfileImageRepository;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
import com.social.profile.repository.TagRepository;
import com.social.profile.repository.TagRedisRepository;
import com.social.profile.storage.ProfileImageStorage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 프로필 삭제 — 이미지(메타+스토리지)·태그(usage_count 감소)·위치·Redis 인덱스까지 정리.
 *
 * <p>순서: DB 는 자식(image/tag/location) 먼저 지우고 부모(profile) 삭제(FK 안전).
 * 외부 부수효과(스토리지 파일 삭제, tags DEL)는 <b>커밋 성공 후</b>에만 실행한다.
 * → DB 롤백 시 파일/인덱스가 먼저 날아가지 않는다. 커밋 후 부수효과가 실패하면 orphan 이 남지만
 *   최종적으로 GC 잡/다음 갱신으로 정리 가능.
 *
 * <p>userId 단위 캐시(카드·geo)는 프로필 하나 삭제로 곧장 지울 수 없어 전용 서비스가 판단한다 —
 * 남은 프로필이 있으면 그걸로 다시 세운다. tags 는 profileId 단위라 그냥 지우면 된다.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class DeleteProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileTagRepository profileTagRepository;
    private final ProfileLocationRepository profileLocationRepository;
    private final TagRepository tagRepository;
    private final ProfileImageStorage profileImageStorage;
    private final GeoCacheService geoCacheService;
    private final TagRedisRepository tagRedisRepository;
    private final ProfileCardCacheService profileCardCacheService;
    private final ProfilePreferenceCacheService profilePreferenceCacheService;

    @Transactional
    public void deleteProfile(String userId, String profileId) {
        UUID uid = UUID.fromString(userId);
        UUID pid = UUID.fromString(profileId);

        ProfileEntity profile = profileRepository.findByProfileIdAndUserId(pid, uid)
            .orElseThrow(() -> new IllegalArgumentException(
                "프로필을 찾을 수 없거나 권한이 없습니다: " + profileId));

        // 삭제 전에 커밋 후 정리에 쓸 오브젝트 키 확보.
        List<ProfileImageEntity> images = profileImageRepository.findByProfileId(pid);
        List<String> objectKeys = images.stream()
            .map(ProfileImageEntity::getObjectKey)
            .toList();

        // 태그: usage_count 감소.
        // 정렬 — 감소도 증가와 같은 순서로 잠가야 한다. 이유는 SaveProfileService 쪽에 적어뒀다.
        List<ProfileTagEntity> profileTags = profileTagRepository.findByProfileId(pid);
        profileTags.stream()
            .map(pt -> pt.getTag().getTagId())
            .sorted()
            .forEach(tagRepository::decrementUsage);

        // 자식은 벌크 delete 쿼리가 아니라 방금 읽어온 엔티티로 지운다.
        // 벌크 쿼리는 DB 행만 지우고 세션에 올라온 엔티티는 그대로 남긴다. 남은 엔티티들이
        // 곧 지워질 프로필을 계속 참조하고 있어서, 아래에서 조회 한 번만 나가도 auto-flush 가
        // "unsaved transient instance" 로 터진다. 이 API 가 100% 500 이던 이유가 이거였다.
        profileTagRepository.deleteAll(profileTags);
        profileImageRepository.deleteAll(images);

        // 위치(있으면) 삭제.
        profileLocationRepository.findById(pid).ifPresent(profileLocationRepository::delete);

        // 부모 프로필 삭제.
        profileRepository.delete(profile);

        // 카드 캐시는 userId 단위라 프로필 하나를 지웠다고 무조건 지우면 안 된다
        // → 남은 프로필이 있으면 그걸로 다시 세우고, 없으면 제거(둘 다 커밋 후 반영).
        profileCardCacheService.refreshAfterProfileDeleted(uid, pid);

        // geo:users 도 같은 이유로 userId 단위 판단이 필요하다(남은 프로필 좌표로 재구성).
        geoCacheService.refreshAfterProfileDeleted(uid, pid);

        // 선호값 캐시도 userId 단위 — 남은 프로필 것으로 재구성하거나 제거.
        profilePreferenceCacheService.refreshAfterProfileDeleted(uid, pid);

        // 외부 부수효과는 커밋 후에만.
        registerExternalCleanupAfterCommit(pid, objectKeys);
    }

    private void registerExternalCleanupAfterCommit(UUID profileId, List<String> objectKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String key : objectKeys) {
                    try {
                        profileImageStorage.delete(key);
                    } catch (Exception e) {
                        log.warn("이미지 파일 삭제 실패(orphan 가능): {}", key, e);
                    }
                }
                try {
                    tagRedisRepository.deleteTags(profileId);
                } catch (Exception e) {
                    log.warn("tags SET 제거 실패: profileId={}", profileId, e);
                }
            }
        });
    }
}
