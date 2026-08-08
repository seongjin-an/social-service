package com.social.domain.match;

import com.social.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
    name = "matches",
    indexes = {
        // 중복 매칭 원천 차단 (정렬 저장 lo<hi 전제)
        @Index(name = "idx_matches_pair", columnList = "user_lo_id, user_hi_id", unique = true),
        // "내 매칭 목록" 조회 (양쪽 다 검색되도록 lo/hi 각각)
        @Index(name = "idx_matches_my_matching1", columnList = "user_lo_id, status"),
        @Index(name = "idx_matches_my_matching2", columnList = "user_hi_id, status")
    })
@Entity
public class MatchEntity extends BaseEntity {
    @Id
    @Column(name = "match_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID matchId;

    @Column(name = "user_lo_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userLoId;

    @Column(name = "user_hi_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userHiId;

    @Column(name = "channel_id")
    private Long channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchStatus status;


    private MatchEntity(UUID matchId, UUID userLoId, UUID userHiId) {
        this.matchId = matchId;
        this.userLoId = userLoId;
        this.userHiId = userHiId;
    }

    private MatchEntity(UUID matchId, UUID userLoId, UUID userHiId, MatchStatus status) {
        this(matchId, userLoId, userHiId);
        this.status = status;
    }

    private MatchEntity(UUID matchId, UUID userLoId, UUID userHiId, Long channelId,
        MatchStatus status) {
        this(matchId, userLoId, userHiId);
        this.channelId = channelId;
        this.status = status;
    }

    public static MatchEntity of(UUID matchId, UUID userLoId, UUID userHiId) {
        return new MatchEntity(matchId, userLoId, userHiId, MatchStatus.ACTIVE);
    }


    public void backfillChannel(Long channelId) {
        this.channelId = channelId;
    }

    /** 언매치 — 목록에서 사라지고, 이벤트로 채널도 닫힌다. 이미 UNMATCHED 면 사실상 no-op(멱등). */
    public void unmatch() {
        this.status = MatchStatus.UNMATCHED;
    }

    public boolean isActive() {
        return this.status == MatchStatus.ACTIVE;
    }

    /** 이 매칭의 당사자인가 — 언매치 권한 검사(403)에 쓴다. */
    public boolean isParticipant(UUID userId) {
        return userLoId.equals(userId) || userHiId.equals(userId);
    }

    /** 두 유저 중 내가 아닌 쪽 = 상대. 당사자가 아니면 호출하면 안 된다. */
    public UUID partnerOf(UUID userId) {
        if (userLoId.equals(userId)) {
            return userHiId;
        }
        if (userHiId.equals(userId)) {
            return userLoId;
        }
        throw new IllegalArgumentException("매칭 당사자가 아닙니다: " + userId);
    }

    public List<UUID> participants() {
        return List.of(userLoId, userHiId);
    }
}
