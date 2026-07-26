package com.social.fanout.kafka.message.out;

import com.social.common.ContentMessage;

public record ContentMessageResponsePayload(String userId, ContentMessage message) {}
