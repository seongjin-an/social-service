package com.social.profile.controller;

import com.social.profile.service.ProfileResult;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * 프로필 카드 응답. birthday 는 저장만 하고, 외부로는 계산된 만나이(age)만 노출한다.
 * (추천/매칭 카드가 나이를 쓰므로 서버에서 계산해 내려준다.)
 */
public record ProfileResponse(
    UUID profileId,
    String gender,
    Integer age,
    String bio,
    String prefGender,
    Integer prefAgeMin,
    Integer prefAgeMax,
    Integer prefDistanceKm,
    List<String> tags,
    List<ProfileImageResponse> images
) {
    public static ProfileResponse of(
        ProfileResult result
    ) {
        return new ProfileResponse(
            result.profileId(),
            result.gender(),
            result.age(),
            result.bio(),
            result.prefGender(),
            result.prefAgeMin(),
            result.prefAgeMax(),
            result.prefDistanceKm(),
            result.tags(),
            result.images().stream().map(ProfileImageResponse::from).toList()
        );
    }

    private static Integer calculateAge(LocalDate birthday) {
        if (birthday == null) {
            return null;
        }
        return Period.between(birthday, LocalDate.now()).getYears();
    }
}
