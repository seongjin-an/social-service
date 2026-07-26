package com.social.profile.repository;

import com.social.profile.domain.ProfileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    @Query("""
      SELECT P
      FROM ProfileEntity P
      JOIN FETCH P.profileTagEntities
      WHERE P.userId = :userId
    """)
    List<ProfileEntity> findByUserIdFetching(UUID userId);

    List<ProfileEntity> findByUserId(UUID userId);

    @Query("""
      SELECT P
      FROM ProfileEntity P
      WHERE P.profileId = :profileId
      AND P.userId = :userId
    """)
    Optional<ProfileEntity> findByProfileIdAndUserId(UUID profileId, UUID userId);
}
