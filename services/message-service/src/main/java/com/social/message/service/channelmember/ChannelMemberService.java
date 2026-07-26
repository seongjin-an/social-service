package com.social.message.service.channelmember;

import com.social.message.domain.ChannelMemberEntity;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ChannelMemberService {

    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberCacheService channelMemberCacheService;

    @Transactional
    public void createChannelMember(ChannelMemberDto dto) {
        ChannelMemberEntity entity = dto.toEntity();
        channelMemberRepository.save(entity);
        channelMemberCacheService.invalidate(dto.getChannelId());
    }

    @Transactional
    public void removeChannelMember(ChannelMemberDto dto) {
        channelMemberRepository.deleteByChannelIdAndUserId(dto.getChannelId(), UUID.fromString(dto.getUserId()));
        channelMemberCacheService.invalidate(dto.getChannelId());
    }

    public List<ChannelMember> getChannelMembers(Long channelId) {
        List<ChannelMemberEntity> channelMembers = channelMemberRepository.findByChannelId(channelId);
        return channelMembers.stream()
            .map(ChannelMember::of)
            .toList();
    }

    public Map<String, Long> getReadState(Long channelId) {
        return channelMemberRepository.findByChannelId(channelId)
            .stream()
            .collect(Collectors.toMap(
                m -> m.getUserId().toString(),
                m -> m.getLastReadMessageId() != null ? m.getLastReadMessageId() : 0L
            ));
    }
}
