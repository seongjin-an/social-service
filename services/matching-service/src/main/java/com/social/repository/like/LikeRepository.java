package com.social.repository.like;

import com.social.domain.like.LikeEntity;
import com.social.domain.like.LikeEntity.LikeEntityId;
import com.social.domain.like.LikeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<LikeEntity, LikeEntityId> {

    Boolean existsByFromUserIdAndToUserIdAndTypeIn(UUID to, UUID from, List<LikeType> like);

    /** 나를 좋아한 사람 — idx_likes_to_user(to_user_id, type) 를 그대로 탄다. 최신순. */
    List<LikeEntity> findByToUserIdAndTypeInOrderByCreatedAtDesc(UUID toUserId, List<LikeType> types);
}
