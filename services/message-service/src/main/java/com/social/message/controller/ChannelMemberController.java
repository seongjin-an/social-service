package com.social.message.controller;

import com.social.common.response.ApiResponse;
import com.social.message.service.channelmember.ChannelMember;
import com.social.message.service.channelmember.ChannelMemberDto;
import com.social.message.service.channelmember.ChannelMemberService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/members")
@RestController
public class ChannelMemberController {

    private final ChannelMemberService channelMemberService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<Void> joinChannel(
        @PathVariable Long channelId,
        @RequestHeader("X-User-Id") String userId
    ) {
        channelMemberService.createChannelMember(ChannelMemberDto.of(channelId, userId));
        return ApiResponse.ok(null);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    public ApiResponse<Void> leaveChannel(
        @PathVariable Long channelId,
        @RequestHeader("X-User-Id") String userId
    ) {
        channelMemberService.removeChannelMember(ChannelMemberDto.of(channelId, userId));
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<ChannelMember>> getMembers(@PathVariable Long channelId) {
        return ApiResponse.ok(channelMemberService.getChannelMembers(channelId));
    }

    @GetMapping("/read-state")
    public ApiResponse<Map<String, Long>> getReadState(@PathVariable Long channelId) {
        return ApiResponse.ok(channelMemberService.getReadState(channelId));
    }
}
