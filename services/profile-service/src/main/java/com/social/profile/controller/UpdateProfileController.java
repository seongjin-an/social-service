package com.social.profile.controller;

import com.social.common.web.Response;
import com.social.profile.service.ProfileWriteDto;
import com.social.profile.service.UpdateProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UpdateProfileController {

    private final UpdateProfileService updateProfileService;

    /** 프로필 수정(전체 교체). 바디는 생성과 동일 스키마(ProfileWriteRequest) 재사용. */
    @PutMapping("/api/profiles/{profileId}")
    public Response<Void> update(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId,
        @Valid @RequestBody ProfileWriteRequest request
    ) {
        updateProfileService.updateProfile(profileId, ProfileWriteDto.of(userId, request));
        return Response.ok(null);
    }
}
