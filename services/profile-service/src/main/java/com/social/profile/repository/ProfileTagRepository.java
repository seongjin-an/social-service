package com.social.profile.repository;

import com.social.profile.domain.ProfileTagEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileTagRepository extends JpaRepository<ProfileTagEntity, UUID> {

    @Query("""
      SELECT PT
      FROM ProfileTagEntity PT
      JOIN FETCH PT.tag
      WHERE PT.profile.profileId = :profileId
    """)
    List<ProfileTagEntity> findByProfileId(UUID profileId);

    @Query("""
      SELECT PT
      FROM ProfileTagEntity PT
      JOIN FETCH PT.tag
      WHERE PT.profile.profileId IN :profileIds
    """)
    List<ProfileTagEntity> findByProfileIdIn(List<UUID> profileIds);

    /** 프로필 삭제 시 태그 연결 일괄 삭제(usage_count 감소는 서비스가 별도로 처리). */
    @Modifying
    @Query("delete from ProfileTagEntity pt where pt.profile.profileId = :profileId")
    void deleteByProfileId(@Param("profileId") UUID profileId);
}
