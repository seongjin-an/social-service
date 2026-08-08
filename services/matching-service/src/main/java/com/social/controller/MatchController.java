package com.social.controller;

import com.social.common.StringUtils;
import com.social.common.response.ApiResponse;
import com.social.service.match.MatchQueryService;
import com.social.service.match.MatchView;
import com.social.service.match.UnmatchService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/matches")
@RestController
public class MatchController {

    private final MatchQueryService matchQueryService;
    private final UnmatchService unmatchService;

    /** F-M4 내 ACTIVE 매칭 목록 (상대 카드 + channelId, 최신순). */
    @GetMapping
    public ApiResponse<List<MatchView>> getMyMatches(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(matchQueryService.getMyMatches(StringUtils.fromUuid(userId)));
    }

    /** F-M5 언매치 — 당사자만(403), 이미 해제면 멱등하게 200. */
    @DeleteMapping("/{matchId}")
    public ApiResponse<Void> unmatch(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable UUID matchId
    ) {
        unmatchService.unmatch(StringUtils.fromUuid(userId), matchId);
        return ApiResponse.ok(null);
    }
}
