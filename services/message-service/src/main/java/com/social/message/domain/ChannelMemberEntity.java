package com.social.message.domain;

import com.social.message.domain.ChannelMemberEntity.ChannelMemberEntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@IdClass(ChannelMemberEntityId.class)
@Table(name = "channel_members")
@Entity
public class ChannelMemberEntity {

    @Id
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    private LocalDateTime joinedAt;

    /** OPEN 방에서만 OWNER|MEMBER. DIRECT 는 NULL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 10)
    private ChannelMemberRole role;

    /** DIRECT 채널 멤버 — 역할 개념이 없다. */
    public static ChannelMemberEntity create(Long channelId, UUID userId) {
        return new ChannelMemberEntity(channelId, userId, null, LocalDateTime.now(), null);
    }

    public static ChannelMemberEntity owner(Long channelId, UUID userId) {
        return new ChannelMemberEntity(channelId, userId, null, LocalDateTime.now(),
            ChannelMemberRole.OWNER);
    }

    public static ChannelMemberEntity member(Long channelId, UUID userId) {
        return new ChannelMemberEntity(channelId, userId, null, LocalDateTime.now(),
            ChannelMemberRole.MEMBER);
    }

    public void updateLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }

    /** 방장이 나갈 때 남은 멤버 중 최초 입장자가 물려받는다. */
    public void promoteToOwner() {
        this.role = ChannelMemberRole.OWNER;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class ChannelMemberEntityId {

        private Long channelId;
        private UUID userId;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ChannelMemberEntityId that = (ChannelMemberEntityId) o;
            return Objects.equals(channelId, that.channelId) && Objects.equals(userId,
                that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelId, userId);
        }

        @Override
        public String toString() {
            return "ChannelMemberEntityId{" +
                "channelId=" + channelId +
                ", userId=" + userId +
                '}';
        }
    }
}
