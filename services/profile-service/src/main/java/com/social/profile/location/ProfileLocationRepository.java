package com.social.profile.location;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileLocationRepository extends JpaRepository<ProfileLocationEntity, UUID> {
}
