package com.social.service.match;

import com.social.common.ProfileCard;
import com.social.domain.match.MatchEntity;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 매칭 목록 한 줄 (F-M4).
 *
 * @param channelId 성사 직후 Saga 백필 전이면 {@code null} — 클라이언트는 "채팅방 준비 중"으로 표시한다.
 */
public record MatchView(
    UUID matchId,
    Long channelId,
    ProfileCard partner,
    LocalDateTime matchedAt
) {
    public static MatchView of(MatchEntity match, ProfileCard partner) {
        return new MatchView(match.getMatchId(), match.getChannelId(), partner, match.getCreatedAt());
    }
}
