package com.social.message.service.openchat;

import com.social.message.domain.ChannelEntity;
import com.social.message.domain.RoomCategory;
import java.time.LocalDateTime;

/**
 * 게시판 한 줄 / 방 상세 응답.
 *
 * @param memberCount  가입 인원(비정규화 캐시) — "12/30" 의 왼쪽
 * @param onlineCount  지금 WS 로 붙어 있는 인원 — "● 8명". 근사값이다({@link RoomPresenceService})
 * @param joined       내가 이미 들어가 있는 방인지 — 목록에서 "참여중" 배지를 그리는 데 쓴다
 */
public record OpenRoom(
    Long channelId,
    String title,
    RoomCategory category,
    String ownerId,
    int maxMembers,
    int memberCount,
    long onlineCount,
    boolean joined,
    boolean owner,
    LocalDateTime createdAt
) {

    public static OpenRoom of(ChannelEntity channel, long onlineCount, boolean joined, boolean owner) {
        return new OpenRoom(
            channel.getChannelId(),
            channel.getTitle(),
            channel.getCategory(),
            channel.getOwnerId() != null ? channel.getOwnerId().toString() : null,
            channel.getMaxMembers() != null ? channel.getMaxMembers() : 0,
            channel.getMemberCount() != null ? channel.getMemberCount() : 0,
            onlineCount,
            joined,
            owner,
            channel.getCreatedAt()
        );
    }
}
