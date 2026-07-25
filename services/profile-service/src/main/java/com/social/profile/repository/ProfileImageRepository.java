package com.social.profile.repository;

import com.social.profile.domain.ProfileImageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<ProfileImageEntity> findByProfileProfileId(@Param("profileIds") List<UUID> profileIds);
}
