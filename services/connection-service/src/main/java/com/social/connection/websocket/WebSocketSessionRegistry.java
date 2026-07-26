package com.social.connection.websocket;

import com.social.common.UserId;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
public class WebSocketSessionRegistry {

    // userId → (sessionId → WebSocketSession)
    private final Map<UserId, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void putSession(UserId userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
            .put(session.getId(), session);
        log.debug("Session registered: userId={}, sessionId={}", userId.id(), session.getId());
    }

    public void closeSession(UserId userId, WebSocketSession session) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;

        userSessions.remove(session.getId());
        log.debug("Session removed: userId={}, sessionId={}", userId.id(), session.getId());

        if (userSessions.isEmpty()) {
            sessions.remove(userId);
        }
    }

    public Map<String, WebSocketSession> getSessions(UserId userId) {
        return sessions.getOrDefault(userId, Collections.emptyMap());
    }

    public Map<UserId, Map<String, WebSocketSession>> getAllSessions() {
        return Collections.unmodifiableMap(sessions);
    }
}
