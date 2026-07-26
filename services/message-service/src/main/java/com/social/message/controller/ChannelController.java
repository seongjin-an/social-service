package com.social.message.controller;

import com.social.common.UserId;
import com.social.common.response.ApiResponse;
import com.social.message.controller.request.CreateChannelRequest;
import com.social.message.service.channel.Channel;
import com.social.message.service.channel.ChannelService;
import com.social.message.service.channel.CreateChannel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/channels")
@RestController
public class ChannelController {

    private final ChannelService channelService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<Channel> createChannel(
        @RequestHeader("X-User-Id") String userId,
        @Valid @RequestBody CreateChannelRequest request
    ) {
        Channel channel = channelService.createChannel(CreateChannel.of(request.title()));
        return ApiResponse.ok(channel);
    }

    @GetMapping
    public ApiResponse<Page<Channel>> getChannels(
        @PageableDefault(size = 20, sort = "channelId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(channelService.getChannelPage(pageable));
    }

    @GetMapping("/me")
    public ApiResponse<Page<Channel>> getMyChannels(
        @RequestHeader("X-User-Id") String userId,
        @PageableDefault(size = 20, sort = "channelId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(channelService.getMyChannelPage(UserId.of(userId), pageable));
    }

    @GetMapping("/{channelId}")
    public ApiResponse<Channel> getChannel(@PathVariable Long channelId) {
        return ApiResponse.ok(channelService.getChannel(channelId));
    }
}
