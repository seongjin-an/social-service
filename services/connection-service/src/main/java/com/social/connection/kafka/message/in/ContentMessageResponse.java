package com.social.connection.kafka.message.in;

import com.social.common.ContentMessage;
import com.social.connection.kafka.message.KafkaMessage;

public record ContentMessageResponse(String userId, ContentMessage message) implements KafkaMessage {


}
