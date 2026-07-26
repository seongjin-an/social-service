package com.social.message.service.channel;

import com.social.common.UserId;
import com.social.common.exception.BusinessException;
import com.social.message.domain.ChannelEntity;
import com.social.message.repository.channel.ChannelProjection;
import com.social.message.repository.channel.ChannelRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ChannelService {

    private final ChannelRepository channelRepository;

    @Transactional
    public Channel createChannel(CreateChannel createChannel) {
        ChannelEntity entity = createChannel.toEntity();
        ChannelEntity savedChannelEntity = channelRepository.save(entity);
        return Channel.from(savedChannelEntity);
    }

    public Channel getChannel(Long channelId) {
        ChannelEntity channelEntity = getChannelEntity(channelId);
        return Channel.from(channelEntity);
    }

    public Page<Channel> getChannelPage(Pageable pageable) {
        Page<ChannelEntity> channelEntityPage = channelRepository.findAll(pageable);
        return channelEntityPage.map(Channel::from);
    }

    public Page<Channel> getMyChannelPage(UserId userId, Pageable pageable) {
        UUID uuidUserId = UUID.fromString(userId.id());
        Page<ChannelProjection> myChannels = channelRepository.findMyChannelsByUserId(
            uuidUserId, pageable);
        return myChannels.map(Channel::from);
    }

    private ChannelEntity getChannelEntity(Long channelId) {
        return channelRepository.findById(channelId)
            .orElseThrow(() -> BusinessException.notFound("잘못된 접근입니다."));
    }
}
