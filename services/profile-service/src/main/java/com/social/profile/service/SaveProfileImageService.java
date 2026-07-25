package com.social.profile.service;

import com.social.profile.controller.ProfileImageResponse;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileImageEntity;
import com.social.profile.repository.ProfileImageRepository;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.storage.ProfileImageStorage;
import com.social.profile.storage.StoredImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class SaveProfileImageService {

    private static final int MAX_IMAGES_PER_PROFILE = 6;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/png", "image/webp");

    private final ProfileImageRepository profileImageRepository;
    private final ProfileRepository profileRepository;
    private final ProfileImageStorage profileImageStorage;

    @Transactional
    public List<ProfileImageResponse> saveProfileImages(
        UUID userId, UUID profileId, List<MultipartFile> images, Integer mainIndex) {
        ProfileEntity profile = loadAndAuthorize(userId, profileId);
        validate(profileId, images, mainIndex);

        // 이번 업로드에서 대표(primary)로 지정할 이미지의 인덱스를 결정.
        //  - mainIndex 지정 시: 그 이미지를 대표로. 기존 대표가 있으면 강등(대표는 1장 유지).
        //  - 미지정 시: 기존 대표가 없을 때만 첫 장을 대표로(기존 자동 지정 동작 유지).
        int primaryIndex;
        if (mainIndex != null) {
            primaryIndex = mainIndex;
            profileImageRepository.findPrimaryByProfileId(profileId)
                .ifPresent(existing -> existing.changePrimary(false));
        } else {
            primaryIndex = profileImageRepository.existsPrimaryByProfileId(profileId) ? -1 : 0;
        }

        // 스토리지 쓰기는 DB 트랜잭션으로 롤백이 안 된다(외부 시스템).
        // → 이 트랜잭션이 롤백되면(루프 중 실패 / saveAll 실패 / 커밋 실패) 방금 올린 파일을
        //   보상 삭제하도록 등록. 커밋되면 그대로 둔다.
        List<String> uploadedKeys = new ArrayList<>();
        registerRollbackCleanup(uploadedKeys);

        List<ProfileImageEntity> entities = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            StoredImage stored = profileImageStorage.store(images.get(i));
            uploadedKeys.add(stored.objectKey());   // 성공적으로 올라간 것만 추적
            boolean primary = i == primaryIndex;
            entities.add(ProfileImageEntity.builder()
                .profile(profile)
                .originalFileName(stored.originalFileName())
                .storedFileName(stored.storedFileName())
                .objectKey(stored.objectKey())
                .imageUrl(stored.imageUrl())
                .contentType(stored.contentType())
                .fileSize(stored.fileSize())
                .primaryImage(primary)
                .build());
        }

        profileImageRepository.saveAll(entities);
        return entities.stream().map(ProfileImageResponse::from).toList();
    }

    /** 트랜잭션 롤백 시, 이미 스토리지에 올라간 파일들을 보상 삭제(orphan 방지). */
    private void registerRollbackCleanup(List<String> uploadedKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                for (String key : uploadedKeys) {
                    try {
                        profileImageStorage.delete(key);
                    } catch (Exception e) {
                        // 보상 삭제까지 실패하면 orphan 이 남을 수 있음 → 최종적으로 orphan GC 잡이 정리
                        log.warn("업로드 파일 보상 삭제 실패(orphan 가능): {}", key, e);
                    }
                }
            }
        });
    }

    private ProfileEntity loadAndAuthorize(UUID userId, UUID profileId) {
        ProfileEntity profile = profileRepository.findById(profileId)
            .orElseThrow(() -> new IllegalArgumentException("프로필을 찾을 수 없습니다: " + profileId));
        if (!profile.getUserId().equals(userId)) {
            // TODO: 전역 예외 핸들러에서 403(Forbidden) 으로 매핑
            throw new IllegalStateException("본인 프로필에만 이미지를 올릴 수 있습니다");
        }
        return profile;
    }

    private void validate(UUID profileId, List<MultipartFile> images, Integer mainIndex) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지가 없습니다");
        }
        if (mainIndex != null && (mainIndex < 0 || mainIndex >= images.size())) {
            throw new IllegalArgumentException(
                "mainIndex 는 0 ~ " + (images.size() - 1) + " 범위여야 합니다: " + mainIndex);
        }
        long existing = profileImageRepository.countByProfileId(profileId);
        if (existing + images.size() > MAX_IMAGES_PER_PROFILE) {
            throw new IllegalArgumentException(
                "이미지는 프로필당 최대 " + MAX_IMAGES_PER_PROFILE + "장까지 (현재 " + existing + "장)");
        }
        for (MultipartFile file : images) {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("빈 파일이 포함되어 있습니다");
            }
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                throw new IllegalArgumentException("지원하지 않는 형식입니다: " + file.getContentType());
            }
        }
    }
}
