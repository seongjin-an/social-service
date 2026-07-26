package com.social.profile.controller;

import com.social.profile.service.TagResult;
import java.util.UUID;

public record TagResponse(
    UUID tagId,
    String name,
    Long usageCount
) {
    public static TagResponse of(TagResult result) {
        return new TagResponse(result.tagId(), result.name(), result.usageCount());
    }
}
