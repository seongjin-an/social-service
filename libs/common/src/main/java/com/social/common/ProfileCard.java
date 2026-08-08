package com.social.common;

import java.util.List;
import java.util.UUID;

/**
 * 프로필 카드 캐시({@code profile:card:{userId}})의 값 스키마.
 *
 * <p><b>생산자</b> profile-service — 프로필/이미지 변경 커밋 후 갱신.
 * <b>소비자</b> matching-service — 매칭 목록·받은 좋아요에서 상대 카드를 MGET 으로 배치 조립.
 *
 * <p>스키마를 공용 모듈에 두는 이유: 두 서비스가 같은 Redis 키를 읽고 쓰므로 필드가 어긋나면
 * 조용히 null 카드가 된다. 컴파일 타임에 묶어 드리프트를 막는다. (ContentMessage 와 같은 패턴)
 *
 * <p>Jackson 역직렬화를 위해 필드 추가 시에도 소비자 쪽 파싱이 깨지지 않도록,
 * matching 은 알 수 없는 필드를 무시하는 ObjectMapper 기본 설정에 의존하지 않고 이 레코드를 그대로 쓴다.
 */
public record ProfileCard(
    UUID userId,
    UUID profileId,
    Integer age,
    String gender,
    String bio,
    List<String> tags,
    String imageUrl
) {
    public static ProfileCard of(
        UUID userId, UUID profileId, Integer age, String gender, String bio,
        List<String> tags, String imageUrl
    ) {
        return new ProfileCard(userId, profileId, age, gender, bio,
            tags == null ? List.of() : tags, imageUrl);
    }

    /** 카드 캐시 미스 시 최소 정보(userId 만) — F-M4 규칙 3의 fallback. */
    public static ProfileCard minimal(UUID userId) {
        return new ProfileCard(userId, null, null, null, null, List.of(), null);
    }
}
