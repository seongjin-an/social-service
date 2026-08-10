package com.social.message.service.openchat;

import com.social.common.KeyPrefix;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * "이 방에 지금 몇 명 붙어 있나" — connection-service 가 이미 관리하는 연결 레지스트리를 읽어 답한다.
 * 오픈방 전용 presence 키를 새로 만들지 않은 이유: 접속 상태의 진실은 {@code ws:user:{userId}} 하나이고,
 * 두 곳에 쓰면 둘이 어긋나는 순간부터 어느 쪽도 못 믿게 된다.
 *
 * <pre>
 * ws:user:{userId} → SET of connectionKey (TTL 있음, 연결 해제 시 삭제)
 * </pre>
 *
 * <p>따라서 presence 는 <b>근사값</b>이다. 프로세스가 그냥 죽으면 TTL(기본 몇 분)이 지나기 전까지
 * 그 유저는 접속 중으로 잡힌다. 게시판의 "● 8명"에는 충분하지만 과금·정원 판정에는 쓰면 안 된다
 * (정원은 {@code member_count} 원자적 UPDATE 가 지킨다).
 *
 * <p>목록 화면은 방이 20개면 멤버가 수백 명이 될 수 있어서, 유저마다 EXISTS 를 왕복하면
 * 게시판 한 장에 수백 번 왕복한다. 그래서 전부 <b>파이프라인 한 번</b>으로 묶는다.
 */
@RequiredArgsConstructor
@Service
public class RoomPresenceService {

    private final StringRedisTemplate redisTemplate;

    /** 넘긴 유저 중 지금 접속 중인 사람만 골라 돌려준다. Redis 왕복 1회. */
    public Set<String> onlineAmong(Collection<String> userIds) {
        List<String> targets = List.copyOf(new LinkedHashSet<>(userIds));
        if (targets.isEmpty()) {
            return Set.of();
        }

        List<Object> replies = redisTemplate.executePipelined((RedisCallback<Object>) connection ->
            pipelineExists(connection, targets));

        Set<String> online = new HashSet<>();
        for (int i = 0; i < targets.size() && i < replies.size(); i++) {
            if (Boolean.TRUE.equals(replies.get(i))) {
                online.add(targets.get(i));
            }
        }
        return online;
    }

    public long countOnline(Collection<UUID> memberIds) {
        return onlineAmong(memberIds.stream().map(UUID::toString).toList()).size();
    }

    /**
     * 파이프라인 모드에서는 각 명령이 즉시 null 을 반환하고 결과는 위 {@code replies} 로 한꺼번에 온다.
     * 콜백은 반드시 null 을 반환해야 한다 — 값을 돌려주면 RedisTemplate 이 예외를 던진다.
     */
    private Object pipelineExists(RedisConnection connection, List<String> userIds) {
        for (String userId : userIds) {
            connection.keyCommands()
                .exists((KeyPrefix.WEBSOCKET_USER + userId).getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }
}
