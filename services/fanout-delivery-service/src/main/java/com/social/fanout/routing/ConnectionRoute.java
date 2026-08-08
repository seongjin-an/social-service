package com.social.fanout.routing;

import java.util.Set;

/**
 * 한 유저의 WS 접속 경로 조회 결과.
 *
 * @param online      {@code ws:user:{userId}} 에 접속 키가 하나라도 있었는지 (오프라인 판정용)
 * @param instanceIds 그중 살아 있는 연결이 붙어 있는 connection-service 인스턴스 id 집합
 *
 * <p>{@code online} 과 {@code instanceIds} 를 따로 두는 이유: TTL 만료로 접속 키만 남고 연결 정보가
 * 사라진 상태(stale)는 "오프라인"이 아니라 "보낼 곳이 없음"이다. 미읽음 카운터는 전자에만 올려야 한다.
 */
public record ConnectionRoute(boolean online, Set<String> instanceIds) {

    public static ConnectionRoute offline() {
        return new ConnectionRoute(false, Set.of());
    }
}
