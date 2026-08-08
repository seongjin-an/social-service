package com.social.profile.service;

import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileImageEntity;
import com.social.profile.location.GeoRedisRepository;
import com.social.profile.location.ProfileLocationRepository;
import com.social.profile.repository.ProfileImageRepository;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
import com.social.profile.repository.TagRedisRepository;
import com.social.profile.repository.TagRepository;
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
 * 외부 부수효과(스토리지 파일 삭제, geo ZREM, tags DEL)는 <b>커밋 성공 후</b>에만 실행한다.
 * → DB 롤백 시 파일/인덱스가 먼저 날아가지 않는다. 커밋 후 부수효과가 실패하면 orphan 이 남지만
 *   최종적으로 GC 잡/다음 갱신으로 정리 가능.
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
    private final GeoRedisRepository geoRedisRepository;
    private final TagRedisRepository tagRedisRepository;
    private final ProfileCardCacheService profileCardCacheService;

    @Transactional
    public void deleteProfile(String userId, String profileId) {
        UUID uid = UUID.fromString(userId);
        UUID pid = UUID.fromString(profileId);

        ProfileEntity profile = profileRepository.findByProfileIdAndUserId(pid, uid)
            .orElseThrow(() -> new IllegalArgumentException(
                "프로필을 찾을 수 없거나 권한이 없습니다: " + profileId));

        // 삭제 전에 커밋 후 정리에 쓸 오브젝트 키 확보.
        List<String> objectKeys = profileImageRepository.findByProfileId(pid).stream()
            .map(ProfileImageEntity::getObjectKey)
            .toList();

        // 태그: usage_count 감소 후 연결 삭제.
        List<UUID> tagIds = profileTagRepository.findByProfileId(pid).stream()
            .map(pt -> pt.getTag().getTagId())
            .toList();
        tagIds.forEach(tagRepository::decrementUsage);
        profileTagRepository.deleteByProfileId(pid);

        // 이미지 메타 삭제.
        profileImageRepository.deleteByProfileId(pid);

        // 위치(있으면) 삭제.
        profileLocationRepository.findById(pid).ifPresent(profileLocationRepository::delete);

        // 부모 프로필 삭제.
        profileRepository.delete(profile);

        // 카드 캐시는 userId 단위라 프로필 하나를 지웠다고 무조건 지우면 안 된다
        // → 남은 프로필이 있으면 그걸로 다시 세우고, 없으면 제거(둘 다 커밋 후 반영).
        profileCardCacheService.refreshAfterProfileDeleted(uid, pid);

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
                    geoRedisRepository.remove(profileId);
                } catch (Exception e) {
                    log.warn("geo:users 제거 실패: profileId={}", profileId, e);
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
