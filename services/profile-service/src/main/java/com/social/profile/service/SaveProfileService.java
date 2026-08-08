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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class SaveProfileService {

    private final ProfileRepository profileRepository;
    private final TagRepository tagRepository;
    private final ProfileTagRepository profileTagRepository;
    private final TagResolver tagResolver;
    private final TagCacheSynchronizer tagCacheSynchronizer;
    private final ProfileCardCacheService profileCardCacheService;
    private final ProfilePreferenceCacheService profilePreferenceCacheService;

    @Transactional
    public String saveProfile(ProfileWriteDto dto) {
        UUID profileId = UuidV7Generator.generate();
        String profileStrId = profileId.toString();

        ProfileEntity profileEntity = dto.toProfileEntity(profileId);
        profileRepository.save(profileEntity);

        List<String> rawTags = dto.tags();
        if (rawTags == null || rawTags.isEmpty()) {
            // 태그 없는 프로필도 카드는 있어야 한다(매칭 목록에 노출됨).
            profileCardCacheService.refreshAfterCommit(dto.userId(), profileId);
            profilePreferenceCacheService.refreshAfterCommit(dto.userId(), profileId);
            return profileStrId;
        }

        // 정규화 기준 중복 제거 — "Java"/"java" 를 하나로. 안 하면 uk_profile_tag(profile_id, tag_id) 위반.
        List<String> distinctTags = rawTags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .collect(Collectors.toMap(TagEntity::normalize, tag -> tag, (first, dup) -> first, LinkedHashMap::new))
            .values().stream().toList();

        List<UUID> tagIds = distinctTags.stream().map(tagResolver::resolveId).toList();

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
        tagCacheSynchronizer.syncAfterCommit(profileId, tagIds);

        // 프로필 카드 캐시 profile:card:{userId} 갱신 (매칭 목록이 MGET 으로 읽음).
        profileCardCacheService.refreshAfterCommit(dto.userId(), profileId);

        // 추천 선호값 캐시 profile:pref:{userId} 갱신 (P2 피드의 반경/성별/나이 필터 입력).
        profilePreferenceCacheService.refreshAfterCommit(dto.userId(), profileId);

        return profileStrId;
    }
}
