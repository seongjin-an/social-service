package com.social.profile.service;

import com.social.profile.domain.TagEntity;
import java.util.UUID;

public record TagResult(
    UUID tagId,
    String name,
    Long usageCount
) {
    public static TagResult from(TagEntity entity) {
        return new TagResult(entity.getTagId(), entity.getName(), entity.getUsageCount());
    }
}
