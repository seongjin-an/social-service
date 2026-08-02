package com.social.controller;

import com.social.domain.like.LikeType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class LikeRequest {
    @NotNull
    private UUID toUserId;

    @NotNull
    private LikeType likeType;
}
