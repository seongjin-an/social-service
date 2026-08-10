package com.social.message.controller;

import com.social.common.response.ApiResponse;
import com.social.message.controller.request.CreateRoomRequest;
import com.social.message.domain.RoomCategory;
import com.social.message.service.openchat.CreateRoom;
import com.social.message.service.openchat.OpenRoom;
import com.social.message.service.openchat.OpenRoomPage;
import com.social.message.service.openchat.OpenRoomService;
import com.social.message.service.openchat.RoomPresence;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오픈채팅 게시판 (P3). {@code X-User-Id} 는 게이트웨이의 JWT 필터가 넣어준다 —
 * 헤더가 없으면 게이트웨이를 우회한 호출이므로 401 로 떨어진다({@link GlobalExceptionHandler}).
 *
 * <p>대화는 이 컨트롤러를 타지 않는다. 입장하면 기존 WS(`SEND_MESSAGE`)와
 * 기존 히스토리 API(`GET /api/channels/{channelId}/messages`)를 그대로 쓴다.
 */
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
@RestController
public class OpenRoomController {

    private final OpenRoomService openRoomService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<OpenRoom> createRoom(
        @RequestHeader("X-User-Id") String userId,
        @Valid @RequestBody CreateRoomRequest request
    ) {
        CreateRoom command = CreateRoom.of(
            request.title(), request.category(), request.maxMembers(), UUID.fromString(userId));
        return ApiResponse.ok(openRoomService.create(command));
    }

    /**
     * @param category null 이면 "전체" 탭
     * @param q        제목 부분 일치. 지금은 LIKE '%q%' 라 방이 많아지면 풀스캔이 된다 —
     *                 규모가 커지면 schema.sql 의 {@code ft_channel_title} FULLTEXT 로 바꾼다.
     * @param cursor   이전 응답의 nextCursor(= 마지막 방의 channelId). 첫 페이지는 생략.
     */
    @GetMapping
    public ApiResponse<OpenRoomPage> browseRooms(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(required = false) RoomCategory category,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(
            openRoomService.browse(category, q, cursor, size, UUID.fromString(userId)));
    }

    @GetMapping("/me")
    public ApiResponse<OpenRoomPage> myRooms(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(openRoomService.myRooms(UUID.fromString(userId), cursor, size));
    }

    @GetMapping("/{channelId}")
    public ApiResponse<OpenRoom> getRoom(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long channelId
    ) {
        return ApiResponse.ok(openRoomService.get(channelId, UUID.fromString(userId)));
    }

    @GetMapping("/{channelId}/presence")
    public ApiResponse<RoomPresence> getPresence(@PathVariable Long channelId) {
        return ApiResponse.ok(openRoomService.presence(channelId));
    }

    /** 멱등 — 이미 멤버면 현재 방 정보를 그대로 돌려준다. 정원 초과는 409. */
    @PostMapping("/{channelId}/join")
    public ApiResponse<OpenRoom> joinRoom(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long channelId
    ) {
        return ApiResponse.ok(openRoomService.join(channelId, UUID.fromString(userId)));
    }

    /** 멱등 — 멤버가 아니어도 204. */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{channelId}/leave")
    public void leaveRoom(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable Long channelId
    ) {
        openRoomService.leave(channelId, UUID.fromString(userId));
    }
}
