package com.social.profile.repository;

import com.social.profile.domain.TagEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<TagEntity, UUID> {
    Optional<TagEntity> findByName(String name);

    Optional<TagEntity> findByNormalizedName(String normalizedName);

    /** 피커: 정규화 키워드 부분일치, 인기순 상위 20. (q 는 호출측에서 normalize 해서 넘긴다) */
    List<TagEntity> findTop20ByNormalizedNameContainingOrderByUsageCountDesc(String normalizedKeyword);

    /** 피커 기본값: 키워드 없을 때 인기 태그 상위 20. */
    List<TagEntity> findTop20ByOrderByUsageCountDesc();

    /** 부착 횟수 원자적 증가 — 동시성에서도 lost update 없음(DB 레벨 +1). */
    @Modifying
    @Query("update TagEntity t set t.usageCount = t.usageCount + 1 where t.tagId = :tagId")
    void incrementUsage(@Param("tagId") UUID tagId);

    /** 부착 해제 시 원자적 감소. 음수 방지 가드(> 0). */
    @Modifying
    @Query("update TagEntity t set t.usageCount = t.usageCount - 1 where t.tagId = :tagId and t.usageCount > 0")
    void decrementUsage(@Param("tagId") UUID tagId);
}
