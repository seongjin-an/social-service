package com.social.connection.dto;

import lombok.Builder;

public record ConnectionInfo(
    String userId,
    String instanceId,
    String connectionKey,
    String sessionId,
    long connectedAt
) {

    @Builder
    public static ConnectionInfo of(String userId, String instanceId, String connectionKey, String sessionId, long connectedAt) {
        return new ConnectionInfo(userId, instanceId, connectionKey, sessionId, connectedAt);
    }
}
