package com.social.controller;

import com.social.service.like.LikePublishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/api/likes")
    public void like(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody LikeRequest likeRequest) {
        likePublishService.publishLikeRelay(userId, likeRequest.getToUserId().toString(), likeRequest.getLikeType());
    }
}
