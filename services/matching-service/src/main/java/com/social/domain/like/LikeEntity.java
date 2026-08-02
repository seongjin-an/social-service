package com.social.domain.like;

import com.social.domain.like.LikeEntity.LikeEntityId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@IdClass(LikeEntityId.class)
@Table(
    name = "likes",
    indexes = {
        // 나를 좋아한 사람 목록이 필요할듯.
        @Index(name = "idx_likes_to_user", columnList = "to_user_id, type")
    })
@Entity
public class LikeEntity {
    @Id
    @Column(name = "from_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID fromUserId;

    @Id
    @Column(name = "to_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID toUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LikeType type;

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    private LikeEntity(UUID fromUserId, UUID toUserId, LikeType type, LocalDateTime createdAt) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.type = type;
        this.createdAt = createdAt;
    }

    public static LikeEntity of(UUID fromUserId, UUID toUserId, LikeType type) {
        return new LikeEntity(fromUserId, toUserId, type, LocalDateTime.now());
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class LikeEntityId {
        private UUID fromUserId;
        private UUID toUserId;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LikeEntityId that = (LikeEntityId) o;
            return Objects.equals(fromUserId, that.fromUserId) && Objects.equals(
                toUserId, that.toUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fromUserId, toUserId);
        }

        @Override
        public String toString() {
            return "LikeEntityId{" +
                "fromUserId=" + fromUserId +
                ", toUserId=" + toUserId +
                '}';
        }
    }
}
