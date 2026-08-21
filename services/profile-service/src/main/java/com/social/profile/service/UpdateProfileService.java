package com.social.profile.service;

import com.social.common.UuidV7Generator;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileTagEntity;
import com.social.profile.domain.TagEntity;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
import com.social.profile.repository.TagRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 수정 (PUT, 전체 교체 의미). 본체 필드 갱신 + 태그 diff 동기화.
 * 태그는 정규화 기준으로 추가분/제거분을 계산해 profile_tag 를 맞추고, usage_count 를 증감하며,
 * 최종 태그 집합을 Redis(tags:{profileId})에 커밋 후 반영한다.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UpdateProfileService {

    private final ProfileRepository profileRepository;
    private final TagRepository tagRepository;
    private final ProfileTagRepository profileTagRepository;
    private final TagWriter tagWriter;
    private final TagCacheSynchronizer tagCacheSynchronizer;
    private final ProfileCardCacheService profileCardCacheService;
    private final ProfilePreferenceCacheService profilePreferenceCacheService;

    @Transactional
    public void updateProfile(String profileId, ProfileWriteDto dto) {
        UUID pid = UUID.fromString(profileId);
        ProfileEntity profile = profileRepository.findByProfileIdAndUserId(pid, dto.userId())
            .orElseThrow(() -> new IllegalArgumentException(
                "프로필을 찾을 수 없거나 권한이 없습니다: " + profileId));

        // 본체 가변 필드 갱신 (managed 엔티티 → dirty checking 으로 커밋 시 UPDATE).
        profile.update(dto.gender(), dto.birthday(), dto.bio(),
            dto.prefGender(), dto.prefAgeMin(), dto.prefAgeMax(), dto.prefDistanceKm());

        List<UUID> finalTagIds = syncTags(profile, dto.tags());

        // 최종 태그 집합을 Redis 에 반영(커밋 후, 멱등).
        tagCacheSynchronizer.syncAfterCommit(pid, finalTagIds);

        // 카드 표시 내용(나이·소개·태그)이 바뀌었으므로 카드 캐시도 갱신.
        profileCardCacheService.refreshAfterCommit(dto.userId(), pid);

        // pref* 도 이 요청으로 교체되므로 선호값 캐시도 함께 갱신(안 하면 피드가 옛 반경으로 돈다).
        profilePreferenceCacheService.refreshAfterCommit(dto.userId(), pid);
    }

    /**
     * 태그 diff 동기화. 반환값은 갱신 후 최종 tagId 목록(Redis 반영용).
     */
    private List<UUID> syncTags(ProfileEntity profile, List<String> rawTags) {
        UUID pid = profile.getProfileId();

        // 기존: normalizedName -> ProfileTagEntity
        Map<String, ProfileTagEntity> existingByNorm = profileTagRepository.findByProfileId(pid).stream()
            .collect(Collectors.toMap(
                pt -> pt.getTag().getNormalizedName(), pt -> pt, (a, b) -> a, LinkedHashMap::new));

        // 신규: normalizedName -> rawName (정규화 기준 중복 제거, 순서 보존)
        Map<String, String> newByNorm = new LinkedHashMap<>();
        if (rawTags != null) {
            for (String raw : rawTags) {
                if (raw != null && !raw.isBlank()) {
                    newByNorm.putIfAbsent(TagEntity.normalize(raw), raw);
                }
            }
        }

        // 제거분: 기존에 있으나 신규에 없는 것 → profile_tag 삭제 + usage_count -1
        Map<String, ProfileTagEntity> removedByNorm = existingByNorm.entrySet().stream()
            .filter(e -> !newByNorm.containsKey(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        // 태그 행을 잠그는 작업(카운트 증감 · 신규 태그 확보)을 이름 하나의 순서로 몰아서 먼저 끝낸다.
        // 제거분과 추가분을 따로 돌리면, 두 프로필이 같은 두 태그를 반대로 갈아끼울 때
        // 한쪽은 A→B 순서로 다른 쪽은 B→A 순서로 잠가서 데드락이 난다.
        TreeSet<String> affected = new TreeSet<>(removedByNorm.keySet());
        newByNorm.keySet().stream().filter(norm -> !existingByNorm.containsKey(norm)).forEach(affected::add);

        Map<String, UUID> addedIdByNorm = new LinkedHashMap<>();
        for (String norm : affected) {
            ProfileTagEntity removed = removedByNorm.get(norm);
            if (removed != null) {
                tagRepository.decrementUsage(removed.getTag().getTagId());
            } else {
                UUID tagId = tagWriter.getOrCreateId(newByNorm.get(norm));   // 추가(get-or-create)
                addedIdByNorm.put(norm, tagId);
                tagRepository.incrementUsage(tagId);
            }
        }

        // 여기부터는 profile_tag 만 건드린다. 태그 행은 위에서 이미 다 잠갔으니 더 기다릴 게 없다.
        if (!removedByNorm.isEmpty()) {
            profileTagRepository.deleteAll(removedByNorm.values());
        }
        if (!addedIdByNorm.isEmpty()) {
            List<ProfileTagEntity> toAdd = addedIdByNorm.values().stream()
                .map(tagId -> ProfileTagEntity.of(
                    UuidV7Generator.generate(), profile, tagRepository.getReferenceById(tagId)))
                .toList();
            profileTagRepository.saveAll(toAdd);
        }

        // 최종 tagId 목록 — 유지된 건 기존 id, 새로 붙은 건 방금 확보한 id.
        List<UUID> finalTagIds = newByNorm.keySet().stream()
            .map(norm -> {
                ProfileTagEntity existing = existingByNorm.get(norm);
                return existing != null ? existing.getTag().getTagId() : addedIdByNorm.get(norm);
            })
            .toList();

        return finalTagIds;
    }
}
