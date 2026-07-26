package com.social.profile.service;

import com.social.profile.domain.ProfileEntity;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * 프로필 카드 응답. birthday 는 저장만 하고, 외부로는 계산된 만나이(age)만 노출한다.
 * (추천/매칭 카드가 나이를 쓰므로 서버에서 계산해 내려준다.)
 */
public record ProfileResult(
    UUID profileId,
    String gender,
    Integer age,
    String bio,
    String prefGender,
    Integer prefAgeMin,
    Integer prefAgeMax,
    Integer prefDistanceKm,
    List<String> tags,
    List<ProfileImageResult> images
) {
    public static ProfileResult of(
        ProfileEntity profile,
        List<String> tags,
        List<ProfileImageResult> images
    ) {
        return new ProfileResult(
            profile.getProfileId(),
            profile.getGender() == null ? null : profile.getGender().name(),
            calculateAge(profile.getBirthday()),
            profile.getBio(),
            profile.getPrefGender() == null ? null : profile.getPrefGender().name(),
            profile.getPrefAgeMin(),
            profile.getPrefAgeMax(),
            profile.getPrefDistanceKm(),
            tags,
            images
        );
    }

    private static Integer calculateAge(LocalDate birthday) {
        if (birthday == null) {
            return null;
        }
        return Period.between(birthday, LocalDate.now()).getYears();
    }
}
