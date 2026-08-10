package com.social.message.controller.request;

import com.social.message.domain.RoomCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param maxMembers 정원. 최소 2(혼자 있는 오픈방은 의미가 없다), 최대 500 —
 *                   presence 를 파이프라인 한 번으로 세는 구조라 방 하나가 무한히 커지면 안 된다.
 *                   (수천 명 방의 fanout 은 P4 부하 시나리오에서 따로 다룬다.)
 */
public record CreateRoomRequest(
    @NotBlank @Size(max = 100) String title,
    @NotNull RoomCategory category,
    @NotNull @Min(2) @Max(500) Integer maxMembers
) {}
