package com.social.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Cloud Gateway 필터 팩토리 네이밍 규칙: {Name}GatewayFilterFactory
 * → application.yml 에서 "JwtAuthentication" 으로 참조
 *
 * 동작:
 *   1) JWT 서명·만료 검증
 *   2) Redis 블랙리스트(BL:{jti}) 조회 — 로그아웃된 토큰 차단
 *   3) X-User-Id / X-User-Role 헤더를 다운스트림으로 전달
 */
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final String BLACKLIST_PREFIX = "BL:";

    private static final List<String> PERMIT_ALL = List.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/refresh"
    );

    private final SecretKey secretKey;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationGatewayFilterFactory(
            @Value("${jwt.secret}") String secret,
            ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.secretKey     = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            if (isPermitAll(path)) {
                return chain.filter(exchange);
            }

            // 브라우저 WebSocket은 커스텀 헤더 설정 불가 → ?token= 쿼리 파라미터 폴백
            String token = null;
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = exchange.getRequest().getQueryParams().getFirst("token");
            }

            if (token == null) {
                return unauthorized(exchange);
            }
            Claims claims;
            try {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException | IllegalArgumentException e) {
                return unauthorized(exchange);
            }

            String jti = claims.getId();

            // 블랙리스트 확인 (reactive) — 로그아웃된 토큰이면 즉시 차단
            return redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return unauthorized(exchange);
                        }

                        String rawName = claims.get("name", String.class);
                        String encodedName = rawName != null
                                ? URLEncoder.encode(rawName, StandardCharsets.UTF_8)
                                : "";

                        ServerWebExchange mutated = exchange.mutate()
                                .request(r -> r
                                        .header("X-User-Id",   claims.getSubject())
                                        .header("X-User-Role", claims.get("role", String.class))
                                        .header("X-User-Name", encodedName)
                                )
                                .build();

                        return chain.filter(mutated);
                    });
        };
    }

    private boolean isPermitAll(String path) {
        return PERMIT_ALL.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {}
}
