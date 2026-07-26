package com.social.profile.service;

import com.social.profile.domain.ProfileImageEntity;
import com.social.profile.repository.ProfileImageRepository;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.storage.ProfileImageStorage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 프로필 이미지 관리: 목록 조회 / 삭제(스토리지 포함·대표 승계) / 대표 변경 / 순서 변경.
 * 모든 작업은 프로필 소유권(X-User-Id)을 먼저 검증한다.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProfileImageManageService {

    private final ProfileRepository profileRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileImageStorage profileImageStorage;

    public List<ProfileImageResult> getImages(String userId, String profileId) {
        UUID pid = authorize(userId, profileId);
        return profileImageRepository.findByProfileIdOrderBySortOrder(pid).stream()
            .map(ProfileImageResult::from)
            .toList();
    }

    /** 이미지 삭제. 대표를 지우면 남은 것 중 sortOrder 최소를 대표로 승계. 파일은 커밋 후 정리. */
    @Transactional
    public void deleteImage(String userId, String profileId, String imageId) {
        UUID pid = authorize(userId, profileId);
        UUID imgId = UUID.fromString(imageId);

        List<ProfileImageEntity> images = profileImageRepository.findByProfileIdOrderBySortOrder(pid);
        ProfileImageEntity target = images.stream()
            .filter(i -> i.getId().equals(imgId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다: " + imageId));

        boolean wasPrimary = Boolean.TRUE.equals(target.getPrimaryImage());
        String objectKey = target.getObjectKey();

        images.remove(target);
        profileImageRepository.delete(target);

        if (wasPrimary && !images.isEmpty()) {
            images.get(0).changePrimary(true);   // sortOrder 최소 = 목록 첫 장 승계
        }

        registerStorageDeleteAfterCommit(objectKey);
    }

    /** 기존 이미지를 대표로 지정. 이전 대표는 강등(대표 1장 유지). */
    @Transactional
    public void setPrimary(String userId, String profileId, String imageId) {
        UUID pid = authorize(userId, profileId);
        UUID imgId = UUID.fromString(imageId);

        List<ProfileImageEntity> images = profileImageRepository.findByProfileIdOrderBySortOrder(pid);
        ProfileImageEntity target = images.stream()
            .filter(i -> i.getId().equals(imgId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다: " + imageId));

        if (Boolean.TRUE.equals(target.getPrimaryImage())) {
            return;   // 이미 대표 → no-op
        }
        images.stream()
            .filter(i -> Boolean.TRUE.equals(i.getPrimaryImage()))
            .forEach(i -> i.changePrimary(false));
        target.changePrimary(true);
    }

    /** 카드 표시 순서 변경. imageIds 는 프로필의 이미지 전체를 원하는 순서로 나열해야 한다. */
    @Transactional
    public void reorder(String userId, String profileId, List<UUID> imageIds) {
        UUID pid = authorize(userId, profileId);

        List<ProfileImageEntity> images = profileImageRepository.findByProfileId(pid);
        Map<UUID, ProfileImageEntity> byId = images.stream()
            .collect(Collectors.toMap(ProfileImageEntity::getId, i -> i));

        if (imageIds.size() != images.size() || !byId.keySet().containsAll(imageIds)) {
            throw new IllegalArgumentException("이미지 순서 목록이 프로필 이미지와 일치하지 않습니다");
        }
        for (int i = 0; i < imageIds.size(); i++) {
            byId.get(imageIds.get(i)).changeSortOrder(i);
        }
    }

    private UUID authorize(String userId, String profileId) {
        UUID uid = UUID.fromString(userId);
        UUID pid = UUID.fromString(profileId);
        profileRepository.findByProfileIdAndUserId(pid, uid)
            .orElseThrow(() -> new IllegalArgumentException(
                "프로필을 찾을 수 없거나 권한이 없습니다: " + profileId));
        return pid;
    }

    private void registerStorageDeleteAfterCommit(String objectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    profileImageStorage.delete(objectKey);
                } catch (Exception e) {
                    log.warn("이미지 파일 삭제 실패(orphan 가능): {}", objectKey, e);
                }
            }
        });
    }
}
