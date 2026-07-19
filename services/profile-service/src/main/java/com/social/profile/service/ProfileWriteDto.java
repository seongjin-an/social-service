package com.social.profile.service;

import com.social.common.Gender;
import com.social.profile.controller.ProfileWriteRequest;
import com.social.profile.domain.ProfileEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfileWriteDto(
    UUID userId,
    Gender gender,
    LocalDate birthday,
    String bio,
    List<String> tags,
    Gender prefGender,
    Integer prefAgeMin,
    Integer prefAgeMax,
    Integer prefDistanceKm
) {

    public ProfileWriteDto {
        if (userId == null) {
            throw new IllegalArgumentException("Invalid UserId");
        }
    }

    public static ProfileWriteDto of(String userId, ProfileWriteRequest request) {
        return new ProfileWriteDto(
            UUID.fromString(userId),
            request.gender(),
            request.birthday(),
            request.bio(),
            request.tags(),
            request.prefGender(),
            request.prefAgeMin(),
            request.prefAgeMax(),
            request.prefDistanceKm()
        );
    }

    public ProfileEntity toProfileEntity(UUID profileId) {
        return ProfileEntity.of(
            profileId, userId, gender, birthday, bio, prefGender, prefAgeMin, prefAgeMax, prefDistanceKm);
    }
}
