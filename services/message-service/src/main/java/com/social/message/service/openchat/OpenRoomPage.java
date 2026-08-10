package com.social.message.service.openchat;

import java.util.List;

/**
 * 게시판 한 페이지.
 *
 * @param nextCursor 다음 페이지의 시작점(마지막 방의 channelId). {@code null} 이면 끝.
 *                   추천 피드의 커서와 달리 버전이 없다 — 정렬 키가 단조 증가 PK 라서
 *                   중간에 방이 생겨도 이미 넘긴 페이지의 순서가 흔들리지 않는다.
 */
public record OpenRoomPage(List<OpenRoom> items, Long nextCursor) {

    public static OpenRoomPage of(List<OpenRoom> items, Long nextCursor) {
        return new OpenRoomPage(items, nextCursor);
    }
}
