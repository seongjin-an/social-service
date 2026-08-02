package com.social.repository.like;

import com.social.domain.like.LikeEntity;
import com.social.domain.like.LikeEntity.LikeEntityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<LikeEntity, LikeEntityId> {

}
