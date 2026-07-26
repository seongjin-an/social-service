package com.social.connection.websocket;

import com.social.common.Constant;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {


    public static final String X_USER_ID   = "X-User-Id";
    public static final String X_USER_NAME = "X-User-Name";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        String userIdHeader   = request.getHeaders().getFirst(X_USER_ID);
        String userNameHeader = request.getHeaders().getFirst(X_USER_NAME);
        log.info("[BeforeHandshake] userId={}", userIdHeader);
        if (userIdHeader == null) {
            return false;
        }

        String userName = userNameHeader != null
                ? URLDecoder.decode(userNameHeader, StandardCharsets.UTF_8)
                : "";
        attributes.put(Constant.USER_ID,   userIdHeader);
        attributes.put(Constant.USER_NAME, userName);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler wsHandler, Exception exception) {

    }
}
