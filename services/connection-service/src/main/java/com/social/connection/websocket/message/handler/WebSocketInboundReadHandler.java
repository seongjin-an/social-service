package com.social.connection.websocket.message.handler;

import com.social.common.Constant;
import com.social.connection.kafka.KafkaProducer;
import com.social.connection.kafka.message.out.ReadContentMessageRequest;
import com.social.connection.websocket.message.WebSocketMessageProcessor;
import com.social.connection.websocket.message.WebSocketMessageType;
import com.social.connection.websocket.message.payload.ReadPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@RequiredArgsConstructor
@Component
public class WebSocketInboundReadHandler implements WebSocketMessageProcessor<ReadPayload> {

    private final KafkaProducer kafkaProducer;

    @Override
    public WebSocketMessageType getSupportedType() {
        return WebSocketMessageType.READ_MESSAGE;
    }

    @Override
    public Class<ReadPayload> getPayloadType() {
        return ReadPayload.class;
    }

    @Override
    public void handle(WebSocketSession session, ReadPayload payload) {
        String userId = (String) session.getAttributes().get(Constant.USER_ID);
        ReadContentMessageRequest request = ReadContentMessageRequest.create(
            payload.channelId(), userId, payload.messageId());
        kafkaProducer.sendReadRelay(request);
    }
}
