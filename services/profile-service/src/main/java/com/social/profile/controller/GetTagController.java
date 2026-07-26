package com.social.profile.controller;

import com.social.common.web.Response;
import com.social.profile.service.GetTagService;
import com.social.profile.service.TagResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
public class GetTagController {

    private final GetTagService getTagService;

    @GetMapping("/api/tags")
    public Response<List<TagResponse>> getTags(
        @RequestParam(value = "q", required = false) String q
    ) {
        List<TagResult> tags = getTagService.searchTags(q);
        return Response.ok(tags.stream().map(TagResponse::of).toList());
    }
}
