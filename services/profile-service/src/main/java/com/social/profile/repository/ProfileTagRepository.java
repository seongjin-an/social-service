package com.social.profile.repository;

import com.social.profile.domain.ProfileTagEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileTagRepository extends JpaRepository<ProfileTagEntity, UUID> {

    @Query("""
      SELECT PT
      FROM ProfileTagEntity PT
      JOIN FETCH PT.tag
      WHERE PT.profile.profileId IN :profileIds
    """)
    List<ProfileTagEntity> findByProfileIdIn(List<UUID> profileIds);
}
