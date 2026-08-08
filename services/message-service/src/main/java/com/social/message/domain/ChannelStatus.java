package com.social.message.domain;

/**
 * ACTIVE — 정상. CLOSED — 언매치 등으로 닫힌 채널(신규 메시지 전송 거부).
 *
 * <p>주의: ddl-auto=update 로 컬럼이 추가되므로 <b>기존 행의 status 는 NULL</b> 이다.
 * 판정 코드는 항상 {@code == CLOSED} 로만 막고, NULL/ACTIVE 는 통과시킨다.
 */
public enum ChannelStatus {
    ACTIVE, CLOSED
}
