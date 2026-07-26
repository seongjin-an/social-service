package com.social.profile.controller;

import com.social.profile.service.ProfileImageResult;
import java.util.UUID;

public record ProfileImageResponse(
    UUID imageId,
    String imageUrl,
    boolean primaryImage,
    Integer sortOrder
) {
    public static ProfileImageResponse from(ProfileImageResult result) {
        return new ProfileImageResponse(
            result.imageId(),
            result.imageUrl(),
            result.primaryImage(),
            result.sortOrder()
        );
    }
}
