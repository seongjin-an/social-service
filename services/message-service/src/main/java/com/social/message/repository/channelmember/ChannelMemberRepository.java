package com.social.message.repository.channelmember;

import com.social.message.domain.ChannelMemberEntity;
import com.social.message.domain.ChannelMemberEntity.ChannelMemberEntityId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChannelMemberRepository extends
    JpaRepository<ChannelMemberEntity, ChannelMemberEntityId> {

    List<ChannelMemberEntity> findByChannelId(Long channelId);

    void deleteByChannelIdAndUserId(Long channelId, UUID userId);

    Optional<ChannelMemberEntity> findByChannelIdAndUserId(Long channelId, UUID userId);

    boolean existsByChannelIdAndUserId(Long channelId, UUID userId);

    /** 게시판 한 장에 실린 방들의 멤버를 한 번에 — 방마다 조회하면 페이지당 쿼리가 20번 난다. */
    List<ChannelMemberEntity> findByChannelIdIn(Collection<Long> channelIds);

    /** 오픈방 멤버 아이디만 — presence 계산에 lastReadMessageId 까지 끌고 올 필요가 없다. */
    @Query("SELECT CM.userId FROM ChannelMemberEntity CM WHERE CM.channelId = :channelId")
    List<UUID> findUserIdsByChannelId(Long channelId);

    /** 방장이 나갈 때 물려받을 후임 — 가장 먼저 들어온 사람. */
    @Query("""
        SELECT CM FROM ChannelMemberEntity CM
        WHERE CM.channelId = :channelId AND CM.userId <> :excludedUserId
        ORDER BY CM.joinedAt ASC, CM.userId ASC
    """)
    List<ChannelMemberEntity> findSuccessorCandidates(Long channelId, UUID excludedUserId,
        Pageable pageable);
}
