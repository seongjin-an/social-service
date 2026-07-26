package com.social.message.repository.channel;

import com.social.message.domain.ChannelEntity;
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
}
