package com.social.profile.repository;

import com.social.profile.domain.ProfileTagEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileTagRepository extends JpaRepository<ProfileTagEntity, UUID> {

}
