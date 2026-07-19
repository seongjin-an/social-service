package com.social.user.controller;

import com.social.common.response.ApiResponse;
import com.social.user.dto.LoginRequest;
import com.social.user.dto.RefreshTokenRequest;
import com.social.user.dto.SignUpRequest;
import com.social.user.dto.TokenPairResponse;
import com.social.user.dto.UserResponse;
import com.social.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ApiResponse.ok("회원가입이 완료되었습니다.", userService.signUp(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal String userId,
            HttpServletRequest request) {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String accessToken = authHeader.substring(7);   // "Bearer " 제거
        userService.logout(userId, accessToken);
        return ApiResponse.ok("로그아웃 되었습니다.", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(userService.refresh(request));
    }
}
