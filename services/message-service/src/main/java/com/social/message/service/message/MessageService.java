package com.social.message.service.message;

import com.social.message.domain.MessageEntity;
import com.social.message.repository.message.MessageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageCursorResult getMessageByCursor(MessageCursorRequest cursor) {
        if (cursor.getMessageId() == null || cursor.getMessageId() == -1) {
            List<MessageEntity> messages = messageRepository.findMessagesInitially(
                cursor.getChannelId(), cursor.getOffset(), cursor.getSize());

            return MessageCursorResult.of(
                getNextKey(messages), messages.stream().map(Message::of).toList());
        }

        List<MessageEntity> messages = messageRepository.findMessagesByCursor(
            cursor.getChannelId(), cursor.getMessageId(), cursor.getOffset(), cursor.getSize());

        return MessageCursorResult.of(
            getNextKey(messages), messages.stream().map(Message::of).toList());
    }

    private long getNextKey(List<MessageEntity> messages) {
        return messages.stream().mapToLong(MessageEntity::getMessageId).min().orElse(-1L);
    }
}
