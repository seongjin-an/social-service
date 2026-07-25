package com.social.profile.controller;

import com.social.profile.service.SaveProfileImageService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@RestController
public class SaveProfileImageController {

    private final SaveProfileImageService saveProfileImageService;

    @PostMapping(
        value = "/api/profiles/{profileId}/images",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<ProfileImageResponse> uploadImages(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable("profileId") UUID profileId,
        @RequestParam("images") List<MultipartFile> images,
        @RequestParam(value = "mainIndex", required = false) Integer mainIndex
    ) {
        return saveProfileImageService.saveProfileImages(UUID.fromString(userId), profileId, images, mainIndex);
    }
}
