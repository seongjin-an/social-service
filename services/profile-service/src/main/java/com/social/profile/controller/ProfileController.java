package com.social.profile.controller;

import com.social.common.web.Response;
import com.social.profile.service.ProfileWriteDto;
import com.social.profile.service.ProfileWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ProfileController {

    private final ProfileWriteService profileWriteService;

    @PostMapping("/api/profiles")
    public Response<String> save(
        @RequestHeader("X-User-Id") String userId,
        @Valid @RequestBody ProfileWriteRequest request
    ) {
        ProfileWriteDto dto = ProfileWriteDto.of(userId, request);

        String profileId = profileWriteService.saveProfile(dto);

        return Response.ok(profileId);
    }
}
