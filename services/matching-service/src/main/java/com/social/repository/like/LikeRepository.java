package com.social.repository.like;

import com.social.domain.like.LikeEntity;
import com.social.domain.like.LikeEntity.LikeEntityId;
import com.social.domain.like.LikeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<LikeEntity, LikeEntityId> {

    Boolean existsByFromUserIdAndToUserIdAndTypeIn(UUID to, UUID from, List<LikeType> like);
}
