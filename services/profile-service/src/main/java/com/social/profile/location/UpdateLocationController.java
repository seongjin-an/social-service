package com.social.profile.location;

import com.social.common.web.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class UpdateLocationController {

    private final UpdateLocationService updateLocationService;

    @PutMapping("/api/profiles/{profileId}/location")
    public Response<Void> updateLocation(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") String profileId,
        @Valid @RequestBody UpdateLocationRequest request
    ) {
        updateLocationService.updateLocation(userId, profileId, request.lat(), request.lng());
        return Response.ok(null);
    }
}
