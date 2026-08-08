package com.social.common;

public class KeyPrefix {

    public static final String WEBSOCKET_USER = "ws:user:";
    public static final String WEBSOCKET_CONNECTION = "ws:connection:";
    public static final String CHANNEL_MEMBERS = "channel:members:";

    // unread:{userId}:{channelId} — 오프라인 수신 미읽음 카운터
    public static final String UNREAD_COUNT = "unread:";

    // profile:card:{userId} — 프로필 카드 캐시(JSON). profile-service 가 쓰고 matching(F-M4)이 MGET 으로 읽는다.
    public static final String PROFILE_CARD = "profile:card:";
}
