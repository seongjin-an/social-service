package com.social.controller;

import com.social.common.StringUtils;
import com.social.common.response.ApiResponse;
import com.social.service.like.LikePublishService;
import com.social.service.like.ReceivedLikeQueryService;
import com.social.service.like.ReceivedLikeView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class LikeController {

    private final LikePublishService likePublishService;
    private final ReceivedLikeQueryService receivedLikeQueryService;

    /**
     * F-M1 좋아요/패스 접수. 판정은 비동기(like-relay 컨슈머)라 결과를 기다리지 않고 202 로 끊는다.
     * 성사 여부는 실시간 알림(F-M3) 또는 매칭 목록(F-M4)으로 확인한다.
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/api/likes")
    public void like(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody LikeRequest likeRequest) {
        likePublishService.publishLikeRelay(userId, likeRequest.getToUserId().toString(), likeRequest.getLikeType());
    }

    /** F-M6 나를 좋아한 사람(아직 매칭 전) 목록. */
    @GetMapping("/api/likes/received")
    public ApiResponse<List<ReceivedLikeView>> getReceivedLikes(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(receivedLikeQueryService.getReceivedLikes(StringUtils.fromUuid(userId)));
    }
}
