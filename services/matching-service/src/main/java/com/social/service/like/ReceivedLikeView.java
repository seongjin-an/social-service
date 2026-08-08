package com.social.service.like;

import com.social.common.ProfileCard;
import com.social.domain.like.LikeType;
import java.time.LocalDateTime;

/** 받은 좋아요 한 줄 (F-M6) — 아직 매칭되지 않은 상대만 담긴다. */
public record ReceivedLikeView(
    ProfileCard partner,
    LikeType type,
    LocalDateTime likedAt
) {}
