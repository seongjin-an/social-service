package com.social.common;

public class KeyPrefix {

    public static final String WEBSOCKET_USER = "ws:user:";
    public static final String WEBSOCKET_CONNECTION = "ws:connection:";
    public static final String CHANNEL_MEMBERS = "channel:members:";

    // unread:{userId}:{channelId} — 오프라인 수신 미읽음 카운터
    public static final String UNREAD_COUNT = "unread:";

    // profile:card:{userId} — 프로필 카드 캐시(JSON). profile-service 가 쓰고 matching(F-M4)이 MGET 으로 읽는다.
    public static final String PROFILE_CARD = "profile:card:";

    // profile:pref:{userId} — 추천 필터 선호값(JSON). profile-service 가 쓰고 recommendation(F2-1)이 읽는다.
    public static final String PROFILE_PREF = "profile:pref:";

    // seen:{userId} — 이미 좋아요/패스한 상대 SET. recommendation 소유(피드 제외 필터).
    public static final String SEEN = "seen:";

    // feed:{userId} — 추천 후보 스냅샷 LIST(랭킹 순). recommendation 소유(커서 페이지네이션 기준).
    // 짝 키 feed:ver:{userId} 가 스냅샷 버전을 들고 있어 만료된 커서를 판별한다.
    public static final String FEED = "feed:";
}
