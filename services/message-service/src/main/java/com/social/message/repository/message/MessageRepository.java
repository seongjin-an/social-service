package com.social.message.repository.message;

import com.social.message.domain.MessageEntity;
import com.social.message.domain.MessageEntity.MessageEntityId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<MessageEntity, MessageEntityId> {

    boolean existsByChannelIdAndClientMessageId(Long channelId, String clientMessageId);

    @Query("""
        SELECT M
        FROM MessageEntity M
        WHERE M.channelId = :channelId
        ORDER BY M.messageId DESC
        LIMIT :size OFFSET :offset
    """)
    List<MessageEntity> findMessagesInitially(Long channelId, int offset, int size);

    @Query("""
        SELECT M
        FROM MessageEntity M
        WHERE M.channelId = :channelId
        AND M.messageId < :nextKey
        ORDER BY M.messageId DESC
        LIMIT :size OFFSET :offset
    """)
    List<MessageEntity> findMessagesByCursor(Long channelId, Long nextKey, int offset, int size);

}
