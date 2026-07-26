package com.social.profile.controller;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** 카드 표시 순서 변경 요청 — 프로필 이미지 전체를 원하는 순서로 나열. */
public record ReorderImagesRequest(
    @NotEmpty
    List<UUID> imageIds
) {
}
