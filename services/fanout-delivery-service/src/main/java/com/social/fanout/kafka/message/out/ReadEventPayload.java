package com.social.fanout.kafka.message.out;

// userId: WebSocket 라우팅 대상(수신자), readerId: 실제로 읽은 사람
public record ReadEventPayload(String userId, Long channelId, String readerId, Long lastReadMessageId) {

}
