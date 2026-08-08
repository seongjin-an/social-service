package com.social.recommendation.controller;

import com.social.common.StringUtils;
import com.social.common.response.ApiResponse;
import com.social.recommendation.service.FeedQueryService;
import com.social.recommendation.service.FeedView;
import com.social.recommendation.service.SeenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/feed")
@RestController
public class FeedController {

    private final FeedQueryService feedQueryService;
    private final SeenService seenService;

    /**
     * F2-1 스와이프 후보 피드 — 반경/성별/나이/본 사람 제외가 적용된 카드 목록 + 다음 커서.
     *
     * <p>{@code cursor} 없이 호출하면 후보를 새로 계산한다(= 새로고침). 커서를 주면 그 스냅샷을 이어 읽는다.
     * {@code nextCursor} 가 null 이면 더 없다.
     */
    @GetMapping
    public ApiResponse<FeedView> getFeed(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(feedQueryService.getFeed(StringUtils.fromUuid(userId), cursor, size));
    }

    /**
     * F2-3 노출 기록 — 클라이언트가 실제로 보여준 카드를 알려준다(다음 피드에서 제외).
     *
     * <p>좋아요/패스는 {@code like-relay} 구독으로 자동 기록되므로 이 API 는 <b>보고 넘긴 카드</b>용이다.
     * 멱등이라 재전송해도 안전하다.
     */
    @PostMapping("/seen")
    public ApiResponse<Void> markSeen(
        @RequestHeader("X-User-Id") String userId,
        @Valid @RequestBody SeenRequest request
    ) {
        seenService.markSeen(StringUtils.fromUuid(userId), request.userIds());
        return ApiResponse.ok(null);
    }
}
