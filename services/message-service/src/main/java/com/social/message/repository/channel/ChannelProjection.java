package com.social.message.repository.channel;

public interface ChannelProjection {
    Long getChannelId();
    String getTitle();
    Long getUnreadCount();
}
