package com.social.profile.repository;

import com.social.profile.domain.TagEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<TagEntity, UUID> {
    Optional<TagEntity> findByName(String name);

    Optional<TagEntity> findByNormalizedName(String normalizedName);

    /** 부착 횟수 원자적 증가 — 동시성에서도 lost update 없음(DB 레벨 +1). */
    @Modifying
    @Query("update TagEntity t set t.usageCount = t.usageCount + 1 where t.tagId = :tagId")
    void incrementUsage(@Param("tagId") UUID tagId);
}
