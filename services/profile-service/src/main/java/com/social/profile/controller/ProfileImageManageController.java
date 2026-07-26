package com.social.profile.controller;

import com.social.common.web.Response;
import com.social.profile.service.ProfileImageManageService;
import com.social.profile.service.ProfileImageResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ProfileImageManageController {

    private final ProfileImageManageService profileImageManageService;

    /** 이미지 목록 (sortOrder 순). */
    @GetMapping("/api/profiles/{profileId}/images")
    public Response<List<ProfileImageResponse>> getImages(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId
    ) {
        List<ProfileImageResult> images = profileImageManageService.getImages(userId, profileId);
        return Response.ok(images.stream().map(ProfileImageResponse::from).toList());
    }

    /** 이미지 삭제 (스토리지 파일 포함, 대표 삭제 시 승계). */
    @DeleteMapping("/api/profiles/{profileId}/images/{imageId}")
    public Response<Void> deleteImage(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId,
        @PathVariable("imageId") String imageId
    ) {
        profileImageManageService.deleteImage(userId, profileId, imageId);
        return Response.ok(null);
    }

    /** 기존 이미지를 대표로 지정. */
    @PatchMapping("/api/profiles/{profileId}/images/{imageId}/primary")
    public Response<Void> setPrimary(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId,
        @PathVariable("imageId") String imageId
    ) {
        profileImageManageService.setPrimary(userId, profileId, imageId);
        return Response.ok(null);
    }

    /** 카드 표시 순서 변경. */
    @PatchMapping("/api/profiles/{profileId}/images/order")
    public Response<Void> reorder(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId,
        @Valid @RequestBody ReorderImagesRequest request
    ) {
        profileImageManageService.reorder(userId, profileId, request.imageIds());
        return Response.ok(null);
    }
}
