package com.social.profile.service;

import com.social.common.UuidV7Generator;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileTagEntity;
import com.social.profile.domain.TagEntity;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
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

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ProfileWriteService {

    private final ProfileRepository profileRepository;
    private final TagRepository tagRepository;
    private final ProfileTagRepository profileTagRepository;
    private final TagWriter tagWriter;

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

        return profileStrId;
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
