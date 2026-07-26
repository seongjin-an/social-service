package com.social.profile.controller;

import com.social.common.web.Response;
import com.social.profile.service.DeleteProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class DeleteProfileController {

    private final DeleteProfileService deleteProfileService;

    @DeleteMapping("/api/profiles/{profileId}")
    public Response<Void> delete(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId
    ) {
        deleteProfileService.deleteProfile(userId, profileId);
        return Response.ok(null);
    }
}
