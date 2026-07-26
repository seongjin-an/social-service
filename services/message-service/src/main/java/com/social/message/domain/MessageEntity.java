package com.social.message.domain;

import com.social.message.domain.MessageEntity.MessageEntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@IdClass(MessageEntityId.class)
@Table(name = "message", uniqueConstraints = {
    // 클라이언트 재전송(at-least-once) 시 중복 저장 차단 — NULL은 MySQL에서 중복 허용이라 레거시 메시지와 공존 가능
    @UniqueConstraint(name = "uk_message_client_message_id", columnNames = {"channel_id", "client_message_id"})
})
@Entity
public class MessageEntity extends BaseEntity{
    @Id
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Id
    @Column(name = "message_id", nullable = false)
    private Long messageId; // Redis INCR

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "client_message_id", length = 36)
    private String clientMessageId;

    public static MessageEntity of(Long channelId, Long messageId, UUID userId, String userName, String content, String clientMessageId) {
        return new MessageEntity(channelId, messageId, userId, userName, content, clientMessageId);
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter
    public static class MessageEntityId implements Serializable {
        private Long channelId;
        private Long messageId;

        public static MessageEntityId of(Long channelId, Long messageId) {
            return new MessageEntityId(channelId, messageId);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MessageEntityId that = (MessageEntityId) o;
            return Objects.equals(channelId, that.channelId) && Objects.equals(messageId,
                that.messageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelId, messageId);
        }

        @Override
        public String toString() {
            return "MessageEntityId{" +
                "channelId=" + channelId +
                ", messageId=" + messageId +
                '}';
        }
    }
}
