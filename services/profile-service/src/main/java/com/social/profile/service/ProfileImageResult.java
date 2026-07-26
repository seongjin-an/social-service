package com.social.profile.service;

import com.social.profile.domain.ProfileImageEntity;
import java.util.UUID;

public record ProfileImageResult(
    UUID imageId,
    String imageUrl,
    boolean primaryImage
) {
    public static ProfileImageResult from(ProfileImageEntity entity) {
        return new ProfileImageResult(
            entity.getId(),
            entity.getImageUrl(),
            Boolean.TRUE.equals(entity.getPrimaryImage())
        );
    }
}
