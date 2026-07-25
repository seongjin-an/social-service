package com.social.profile.controller;

import com.social.profile.domain.ProfileImageEntity;
import java.util.UUID;

public record ProfileImageResponse(
    UUID imageId,
    String imageUrl,
    boolean primaryImage
) {
    public static ProfileImageResponse from(ProfileImageEntity entity) {
        return new ProfileImageResponse(
            entity.getId(),
            entity.getImageUrl(),
            Boolean.TRUE.equals(entity.getPrimaryImage())
        );
    }
}
