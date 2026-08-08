package com.social.recommendation.controller;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** 노출 기록 요청 — 방금 화면에 보여준 상대들. */
public record SeenRequest(@NotEmpty List<UUID> userIds) {}
