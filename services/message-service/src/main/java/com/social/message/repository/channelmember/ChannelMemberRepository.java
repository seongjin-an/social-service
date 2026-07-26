package com.social.message.repository.channelmember;

import com.social.message.domain.ChannelMemberEntity;
import com.social.message.domain.ChannelMemberEntity.ChannelMemberEntityId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMemberRepository extends
    JpaRepository<ChannelMemberEntity, ChannelMemberEntityId> {

    List<ChannelMemberEntity> findByChannelId(Long channelId);

    void deleteByChannelIdAndUserId(Long channelId, UUID userId);

    Optional<ChannelMemberEntity> findByChannelIdAndUserId(Long channelId, UUID userId);
}
