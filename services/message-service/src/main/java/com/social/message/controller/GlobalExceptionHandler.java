package com.social.message.controller;

import com.social.common.exception.BusinessException;
import com.social.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * message-service 에는 이게 없어서 {@code BusinessException} 이 전부 500 으로 나가고 있었다.
 * 오픈채팅은 "정원 초과 409 · 없는 방 404 · 미인증 401"을 클라이언트가 구분해야 하므로 필요하다.
 * (matching / recommendation 의 핸들러와 같은 매핑을 쓴다.)
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

    /** X-User-Id 누락 = 게이트웨이를 통과하지 않은 호출 → 미인증(401). */
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

    /**
     * 쿼리 파라미터 타입 불일치 — {@code ?category=NOPE} 처럼 enum 에 없는 값이 온 경우.
     * (IllegalArgumentException 을 상속하지 않아 위 핸들러에 안 걸린다. 두지 않으면 500 이 나간다.)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(e.getName() + " 값이 올바르지 않습니다: " + e.getValue()));
    }

    /** 요청 본문을 못 읽는 경우 — 깨진 JSON, 본문 enum 에 없는 값 등. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.info("[BadRequestBody] {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error("요청 본문을 해석할 수 없습니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("[Unhandled] {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("일시적인 오류가 발생했습니다"));
    }
}
