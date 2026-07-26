package com.social.profile.service;

import com.social.common.UuidV7Generator;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileTagEntity;
import com.social.profile.domain.TagEntity;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
import com.social.profile.repository.TagRedisRepository;
import com.social.profile.repository.TagRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class SaveProfileService {

    private final ProfileRepository profileRepository;
    private final TagRepository tagRepository;
    private final ProfileTagRepository profileTagRepository;
    private final TagWriter tagWriter;
    private final TagRedisRepository tagRedisRepository;

    @Transactional
    public String saveProfile(ProfileWriteDto dto) {
        UUID profileId = UuidV7Generator.generate();
        String profileStrId = profileId.toString();

        ProfileEntity profileEntity = dto.toProfileEntity(profileId);
        profileRepository.save(profileEntity);

        List<String> rawTags = dto.tags();
        if (rawTags == null || rawTags.isEmpty()) {
            return profileStrId;
        }

        // 정규화 기준 중복 제거 — "Java"/"java" 를 하나로. 안 하면 uk_profile_tag(profile_id, tag_id) 위반.
        List<String> distinctTags = rawTags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .collect(Collectors.toMap(TagEntity::normalize, tag -> tag, (first, dup) -> first, LinkedHashMap::new))
            .values().stream().toList();

        List<UUID> tagIds = distinctTags.stream().map(this::resolveTagId).toList();

        // 태그 참조는 프록시(getReferenceById)로 → 불필요한 SELECT 없이 FK 만 사용.
        List<ProfileTagEntity> profileTags = tagIds.stream()
            .map(tagRepository::getReferenceById)
            .map(tag -> ProfileTagEntity.of(UuidV7Generator.generate(), profileEntity, tag))
            .toList();

        profileEntity.add(profileTags);
        profileTagRepository.saveAll(profileTags);

        // 인기 태그 카운트 — 원자적 +1 (부착 수 반영).
        tagIds.forEach(tagRepository::incrementUsage);

        // Redis SET tags:{profileId} 갱신(P2 SINTERCARD 입력) — DB 커밋 성공 후에만 반영.
        // 롤백 시 유령 태그 집합이 안 남고, Redis 만 실패하면 다음 저장/수정에서 복구(멱등).
        registerTagRedisAfterCommit(profileId, tagIds);

        return profileStrId;
    }

    private void registerTagRedisAfterCommit(UUID profileId, List<UUID> tagIds) {
        List<String> tokens = tagIds.stream().map(UUID::toString).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    tagRedisRepository.replaceTags(profileId, tokens);
                } catch (Exception e) {
                    log.warn("tags Redis SET 반영 실패(다음 갱신에서 복구): profileId={}", profileId, e);
                }
            }
        });
    }

    /**
     * 태그를 정규화 기준으로 get-or-create 하여 tag_id 반환.
     * TagWriter(REQUIRES_NEW)에서 만들다 동시 생성 레이스로 UNIQUE 위반이 나면,
     * 그 실패는 별도 트랜잭션에 갇혀 롤백되므로 여기서 재조회로 흡수한다.
     */
    private UUID resolveTagId(String rawName) {
        try {
            return tagWriter.getOrCreateId(rawName);
        } catch (DataIntegrityViolationException race) {
            return tagRepository.findByNormalizedName(TagEntity.normalize(rawName))
                .map(TagEntity::getId)
                .orElseThrow(() -> race);
        }
    }
}
