package com.social.message.service.channelmember;

import com.social.message.domain.ChannelMemberEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ChannelMember {
    private final Long channelId;
    private final String userId;

    public static ChannelMember of(Long channelId, String userId) {
        return new ChannelMember(channelId, userId);
    }

    public static ChannelMember of(ChannelMemberEntity entity) {
        return new ChannelMember(entity.getChannelId(), entity.getUserId().toString());
    }
}
