package com.social.repository.match;

import com.social.domain.match.MatchEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {

}
