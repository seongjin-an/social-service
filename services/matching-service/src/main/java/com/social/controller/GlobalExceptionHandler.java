package com.social.controller;

import com.social.common.exception.BusinessException;
import com.social.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 기능 정의서의 에러 매핑을 실제 상태 코드로 옮긴다: 검증 400 · 미인증 401 · 권한 403 · 없음 404.
 * (이게 없으면 자기 자신 좋아요/남의 매칭 해제 같은 규칙 위반이 전부 500 으로 나간다.)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 도메인 규칙 위반 — 상태 코드를 예외가 들고 있다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.info("[BusinessException] status={}, message={}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * X-User-Id 누락 = 게이트웨이를 통과하지 않은 호출 → 미인증(401).
     * (Spring 기본값은 400 이지만 기능 정의서 기준은 401)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("인증 정보가 없습니다: " + e.getHeaderName()));
    }

    /** @Valid 실패 — 첫 필드 오류만 메시지로 올린다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    /** UUID 형식 오류 등 — 잘못된 입력. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("[Unhandled] {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("일시적인 오류가 발생했습니다"));
    }
}
