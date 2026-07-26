package com.social.message.controller;

import com.social.common.response.ApiResponse;
import com.social.message.service.message.MessageCursorRequest;
import com.social.message.service.message.MessageCursorResult;
import com.social.message.service.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/messages")
@RestController
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<MessageCursorResult> getMessages(
        @PathVariable Long channelId,
        @RequestParam(required = false) Long beforeSeq,
        @RequestParam(defaultValue = "50") int size
    ) {
        MessageCursorRequest cursor = (beforeSeq == null)
            ? MessageCursorRequest.of(channelId)
            : MessageCursorRequest.of(channelId, beforeSeq, 0, size);

        return ApiResponse.ok(messageService.getMessageByCursor(cursor));
    }
}
