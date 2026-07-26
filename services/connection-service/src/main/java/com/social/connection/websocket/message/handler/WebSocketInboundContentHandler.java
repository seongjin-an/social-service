package com.social.connection.websocket.message.handler;

import com.social.common.Constant;
import com.social.common.UserId;
import com.social.connection.kafka.message.out.ContentMessageRequest;
import com.social.connection.kafka.KafkaProducer;
import com.social.connection.websocket.message.WebSocketMessageType;
import com.social.connection.websocket.message.WebSocketMessageProcessor;
import com.social.connection.websocket.message.payload.ContentPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@RequiredArgsConstructor
@Component
public class WebSocketInboundContentHandler implements WebSocketMessageProcessor<ContentPayload> {

    @Value("${server.id}")
    private String serverId;

    private final KafkaProducer kafkaProducer;

    @Override
    public WebSocketMessageType getSupportedType() {
        return WebSocketMessageType.SEND_MESSAGE;
    }

    @Override
    public Class<ContentPayload> getPayloadType() {
        return ContentPayload.class;
    }

    @Override
    public void handle(WebSocketSession session, ContentPayload payload) {
        String userId   = (String) session.getAttributes().get(Constant.USER_ID);
        String userName = (String) session.getAttributes().get(Constant.USER_NAME);
        ContentMessageRequest contentMessageRequest = ContentMessageRequest.of(UserId.of(userId), userName, payload, serverId);
        kafkaProducer.sendContentPayloadByPartitionKey(contentMessageRequest);
    }
}
