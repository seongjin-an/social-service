package com.social.message.repository.channel;

import com.social.message.domain.ChannelEntity;
import com.social.message.domain.ChannelStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {

    @Query("""
        SELECT CH.channelId AS channelId, CH.title AS title,
               (SELECT COUNT(M) FROM MessageEntity M
                WHERE M.channelId = CH.channelId
                AND M.messageId > COALESCE(CM.lastReadMessageId, 0))
               AS unreadCount
        FROM ChannelEntity CH
        JOIN ChannelMemberEntity CM ON CH.channelId = CM.channelId
        WHERE CM.userId = :userId
    """)
    Page<ChannelProjection> findMyChannelsByUserId(UUID userId, Pageable pageable);

    /** Saga 멱등 판정 — MATCH_CREATED/MATCH_UNMATCHED 재수신 시 이 매칭의 채널을 찾는다. */
    Optional<ChannelEntity> findByMatchId(UUID matchId);

    /**
     * 전송 가드용 — 채널 전체를 로드하지 않고 상태만 읽는다.
     * 기존 행은 status 가 NULL 이므로 {@code Optional.empty()} 가 "채널 없음"과 구분되지 않는다.
     * → 호출부는 "CLOSED 인 경우에만 거부"로 판정한다.
     */
    @Query("SELECT CH.status FROM ChannelEntity CH WHERE CH.channelId = :channelId")
    Optional<ChannelStatus> findStatusByChannelId(Long channelId);
}
