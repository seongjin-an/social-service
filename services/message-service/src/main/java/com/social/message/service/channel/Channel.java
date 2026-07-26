package com.social.message.service.channel;

import com.social.message.domain.ChannelEntity;
import com.social.message.repository.channel.ChannelProjection;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Channel {
    private final Long channelId;
    private final String title;
    private final long unreadCount;

    public static Channel from(ChannelEntity channelEntity) {
        return new Channel(channelEntity.getChannelId(), channelEntity.getTitle(), 0L);
    }

    public static Channel from(ChannelProjection channelProjection) {
        Long raw = channelProjection.getUnreadCount();
        return new Channel(channelProjection.getChannelId(), channelProjection.getTitle(), raw != null ? raw : 0L);
    }
}
