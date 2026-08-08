package com.social.recommendation.service;

import com.social.common.ProfileCard;
import java.util.List;

/**
 * 피드 응답 (F2-1).
 *
 * @param items      스와이프 후보 카드 (상위 랭킹 순)
 * @param nextCursor 다음 페이지 커서. {@code null} 이면 더 없음 —
 *                   클라이언트는 이때 "주변에 후보가 없다"를 표시하거나 반경을 넓히도록 안내한다.
 */
public record FeedView(List<FeedItem> items, String nextCursor) {

    /**
     * 카드 한 장 + 추천 근거.
     *
     * @param distanceKm 반경검색 당시 거리(km) — 스냅샷에 담아 둔 값이라 조회 때 GEODIST 를 다시 때리지 않는다
     * @param sharedTags 나와 겹치는 관심사 — "관심사 2개 일치" 같은 추천 이유 표시에 쓴다
     */
    public record FeedItem(ProfileCard profile, double distanceKm, List<String> sharedTags) {}

    public static FeedView of(List<FeedItem> items, String nextCursor) {
        return new FeedView(items, nextCursor);
    }

    public static FeedView empty() {
        return new FeedView(List.of(), null);
    }
}
