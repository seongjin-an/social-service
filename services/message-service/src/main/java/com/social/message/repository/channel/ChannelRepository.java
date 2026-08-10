package com.social.message.repository.channel;

import com.social.message.domain.ChannelEntity;
import com.social.message.domain.ChannelStatus;
import com.social.message.domain.RoomCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


    // ── 오픈채팅 게시판 (F3-2) ────────────────────────────────────────────────
    // 커서는 channel_id 내림차순 키셋이다. 추천 피드처럼 스냅샷/버전이 필요 없는 이유는
    // 정렬 키가 점수가 아니라 단조 증가하는 PK 라서, 새 방이 생겨도 이미 넘긴 페이지의
    // 순서가 흔들리지 않기 때문이다(새 방은 항상 1페이지 위쪽에만 끼어든다).
    //
    // 파라미터 null 을 JPQL 로 넘기지 않는다 — Hibernate 가 `? is null` 의 타입을 못 잡는
    // 경우가 있어서, 호출부에서 cursor 는 Long.MAX_VALUE, keyword 는 "" 로 정규화해 보낸다.
    //
    // ownerId IS NOT NULL 조건: type 컬럼이 나중에 추가돼 기존 일반 채널도 OPEN 으로 남아 있다.
    // 방장이 있는 것만 "게시판에 올라온 방"으로 본다.

    @Query("""
        SELECT CH FROM ChannelEntity CH
        WHERE CH.type = com.social.message.domain.ChannelType.OPEN
          AND CH.status = com.social.message.domain.ChannelStatus.ACTIVE
          AND CH.ownerId IS NOT NULL
          AND CH.channelId < :cursor
          AND (:keyword = '' OR LOWER(CH.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY CH.channelId DESC
    """)
    List<ChannelEntity> browseOpenRooms(
        @Param("keyword") String keyword, @Param("cursor") long cursor, Pageable pageable);

    @Query("""
        SELECT CH FROM ChannelEntity CH
        WHERE CH.type = com.social.message.domain.ChannelType.OPEN
          AND CH.status = com.social.message.domain.ChannelStatus.ACTIVE
          AND CH.ownerId IS NOT NULL
          AND CH.category = :category
          AND CH.channelId < :cursor
          AND (:keyword = '' OR LOWER(CH.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY CH.channelId DESC
    """)
    List<ChannelEntity> browseOpenRoomsByCategory(
        @Param("category") RoomCategory category, @Param("keyword") String keyword,
        @Param("cursor") long cursor, Pageable pageable);

    /** 내가 들어가 있는 오픈방. 닫힌 방은 제외한다(들어가도 메시지를 못 보내므로). */
    @Query("""
        SELECT CH FROM ChannelEntity CH
        JOIN ChannelMemberEntity CM ON CM.channelId = CH.channelId
        WHERE CM.userId = :userId
          AND CH.type = com.social.message.domain.ChannelType.OPEN
          AND CH.status = com.social.message.domain.ChannelStatus.ACTIVE
          AND CH.ownerId IS NOT NULL
          AND CH.channelId < :cursor
        ORDER BY CH.channelId DESC
    """)
    List<ChannelEntity> findMyOpenRooms(
        @Param("userId") UUID userId, @Param("cursor") long cursor, Pageable pageable);

    /**
     * 자리 하나를 원자적으로 예약한다. {@code member_count < max_members} 를 UPDATE 의 WHERE 에
     * 넣었기 때문에, 정원 1자리에 100명이 동시에 몰려도 <b>정확히 1건</b>만 1을 반환한다.
     * (엔티티를 읽어 +1 하고 저장하면 lost update 로 정원이 새는데, 그걸 막는 게 이 쿼리의 목적이다.)
     *
     * @return 1 = 예약 성공, 0 = 정원 초과 또는 방이 닫힘
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE ChannelEntity CH SET CH.memberCount = CH.memberCount + 1
        WHERE CH.channelId = :channelId
          AND CH.status = com.social.message.domain.ChannelStatus.ACTIVE
          AND CH.memberCount < CH.maxMembers
    """)
    int reserveSeat(@Param("channelId") Long channelId);

    /** 퇴장 — 0 미만으로 내려가지 않게 가드를 WHERE 에 둔다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE ChannelEntity CH SET CH.memberCount = CH.memberCount - 1
        WHERE CH.channelId = :channelId AND CH.memberCount > 0
    """)
    int releaseSeat(@Param("channelId") Long channelId);
}
