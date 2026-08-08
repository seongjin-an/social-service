package com.social.common;

import java.util.UUID;

/**
 * 추천 필터 선호값 캐시({@code profile:pref:{userId}})의 값 스키마.
 *
 * <p><b>생산자</b> profile-service — 프로필 생성/수정 커밋 후 갱신.
 * <b>소비자</b> recommendation-service — 피드 조회 시 <b>요청자 본인 것 1건</b>만 GET 한다.
 *
 * <p>카드({@link ProfileCard})와 키를 나눈 이유: 카드는 <b>남에게 보여주는</b> 정보라 MGET 으로 통째로
 * 뿌려진다. 선호값은 <b>본인만 쓰는</b> 필터 입력이므로 같은 값에 섞으면 남의 선호가 함께 노출된다.
 *
 * <p>모든 필드가 nullable 이고 <b>null = 제약 없음</b>이다(선호 미설정 유저도 피드는 받아야 한다).
 * 반경만은 null 이면 소비자 쪽 기본값을 쓴다 — 반경 없는 GEOSEARCH 는 불가능하기 때문.
 *
 * @param prefGender 상대에게 원하는 성별. {@link Gender} 이름 문자열(MALE|FEMALE), null 이면 무관.
 */
public record ProfilePreference(
    UUID userId,
    String prefGender,
    Integer prefAgeMin,
    Integer prefAgeMax,
    Integer prefDistanceKm
) {
    public static ProfilePreference of(
        UUID userId, String prefGender, Integer prefAgeMin, Integer prefAgeMax, Integer prefDistanceKm
    ) {
        return new ProfilePreference(userId, prefGender, prefAgeMin, prefAgeMax, prefDistanceKm);
    }

    /** 캐시 미스 fallback — 아무 제약 없음(반경은 소비자 기본값). 피드가 빈 배열로 죽는 것보다 낫다. */
    public static ProfilePreference unrestricted(UUID userId) {
        return new ProfilePreference(userId, null, null, null, null);
    }
}
