package com.social.message.kafka.message.out;

import com.social.common.ContentMessage;

public record ContentMessageResponsePayload(String userId, ContentMessage message) {}
