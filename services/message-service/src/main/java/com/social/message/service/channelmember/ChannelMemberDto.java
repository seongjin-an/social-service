package com.social.message.service.channelmember;

import com.social.message.domain.ChannelMemberEntity;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ChannelMemberDto {

    private final Long channelId;
    private final String userId;

    public static ChannelMemberDto of(Long channelId, String userId) {
        return new ChannelMemberDto(channelId, userId);
    }

    public ChannelMemberEntity toEntity() {
        return ChannelMemberEntity.create(channelId, UUID.fromString(userId));
    }
}
