package com.social.profile.repository;

import com.social.profile.domain.ProfileImageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileImageRepository extends JpaRepository<ProfileImageEntity, UUID> {

    @Query("select count(i) from ProfileImageEntity i where i.profile.profileId = :profileId")
    long countByProfileId(@Param("profileId") UUID profileId);

    @Query("select case when count(i) > 0 then true else false end "
        + "from ProfileImageEntity i where i.profile.profileId = :profileId and i.primaryImage = true")
    boolean existsPrimaryByProfileId(@Param("profileId") UUID profileId);

    @Query("select i from ProfileImageEntity i "
        + "where i.profile.profileId = :profileId and i.primaryImage = true")
    Optional<ProfileImageEntity> findPrimaryByProfileId(@Param("profileId") UUID profileId);

    @Query("""
      SELECT PI
      FROM ProfileImageEntity PI
      WHERE PI.profile.profileId IN :profileIds
    """)
    List<ProfileImageEntity> findByProfileIdIn(@Param("profileIds") List<UUID> profileIds);

    @Query("""
      SELECT PI
      FROM ProfileImageEntity PI
      WHERE PI.profile.profileId = :profileId
    """)
    List<ProfileImageEntity> findByProfileId(@Param("profileId") UUID profileId);

    /** 카드/관리용 정렬 목록 — sortOrder 오름차순(널은 뒤로), 동률 시 생성순. */
    @Query("""
      SELECT PI
      FROM ProfileImageEntity PI
      WHERE PI.profile.profileId = :profileId
      ORDER BY CASE WHEN PI.sortOrder IS NULL THEN 1 ELSE 0 END, PI.sortOrder ASC, PI.createdAt ASC
    """)
    List<ProfileImageEntity> findByProfileIdOrderBySortOrder(@Param("profileId") UUID profileId);

    /** 프로필 삭제 시 이미지 메타 일괄 삭제(스토리지 파일은 서비스가 커밋 후 정리). */
    @Modifying
    @Query("delete from ProfileImageEntity i where i.profile.profileId = :profileId")
    void deleteByProfileId(@Param("profileId") UUID profileId);
}
