package com.social.message.service.openchat;

/**
 * @param memberCount 가입 인원 — {@code channel_members} 의 실제 행 수(정원 판정의 기준)
 * @param onlineCount 지금 WS 로 붙어 있는 인원 — 근사값({@link RoomPresenceService} 참고)
 */
public record RoomPresence(Long channelId, int memberCount, long onlineCount) {}
