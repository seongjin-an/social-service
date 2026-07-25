package com.social.profile.controller;

import com.social.common.web.Response;
import com.social.profile.service.GetProfileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class GetProfileController {

    private final GetProfileService getProfileService;

    @GetMapping("/api/profiles/me")
    public Response<List<ProfileResponse>> getProfiles(@RequestHeader("X-User-Id") String userId) {
        return Response.ok(getProfileService.getProfiles(userId));
    }
}
