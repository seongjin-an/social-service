package com.social.profile.service;

import com.social.profile.controller.ProfileImageResponse;
import com.social.profile.controller.ProfileResponse;
import com.social.profile.domain.ProfileEntity;
import com.social.profile.domain.ProfileImageEntity;
import com.social.profile.repository.ProfileImageRepository;
import com.social.profile.repository.ProfileRepository;
import com.social.profile.repository.ProfileTagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
public class GetProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileTagRepository profileTagRepository;

    /**
     * 내(userId) 프로필 목록(멀티프로필). 각 프로필의 태그·이미지를 함께 조립해 카드 형태로 반환.
     *
     * <p>N+1 회피: 프로필들을 먼저 조회한 뒤, 태그/이미지는 profileId 리스트로 <b>한 번씩만</b> 배치 조회하고
     * 메모리에서 profileId 기준으로 묶는다. (bag 두 개를 동시에 fetch join 하면 MultipleBagFetchException 이라
     * 별도 쿼리로 나눈다.) 프록시의 식별자 getter(getProfileId)는 초기화를 유발하지 않아 그룹핑 키로 안전.
     */
    public List<ProfileResponse> getProfiles(String userId) {
        List<ProfileEntity> profiles = profileRepository.findByUserId(UUID.fromString(userId));
        if (profiles.isEmpty()) {
            return List.of();
        }

        List<UUID> profileIds = profiles.stream().map(ProfileEntity::getProfileId).toList();

        Map<UUID, List<String>> tagsByProfile = profileTagRepository.findByProfileIdIn(profileIds).stream()
            .collect(Collectors.groupingBy(
                pt -> pt.getProfile().getProfileId(),
                Collectors.mapping(pt -> pt.getTag().getName(), Collectors.toList())));

        Map<UUID, List<ProfileImageResponse>> imagesByProfile = profileImageRepository.findByProfileProfileId(profileIds).stream()
            // 대표 이미지가 먼저 오도록 정렬(카드 썸네일용). groupingBy 는 encounter order 를 보존.
            .sorted(Comparator.comparing((ProfileImageEntity i) -> Boolean.TRUE.equals(i.getPrimaryImage())).reversed())
            .collect(Collectors.groupingBy(
                img -> img.getProfile().getProfileId(),
                Collectors.mapping(ProfileImageResponse::from, Collectors.toList())));

        return profiles.stream()
            .map(profile -> ProfileResponse.of(
                profile,
                tagsByProfile.getOrDefault(profile.getProfileId(), List.of()),
                imagesByProfile.getOrDefault(profile.getProfileId(), List.of())))
            .toList();
    }
}
