package com.social.profile.repository;

import com.social.profile.domain.ProfileImageEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileImageRepository extends JpaRepository<ProfileImageEntity, UUID> {
}
