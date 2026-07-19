package com.social.user.service;

import com.social.common.exception.BusinessException;
import com.social.user.config.JwtTokenProvider;
import com.social.user.domain.User;
import com.social.user.dto.LoginRequest;
import com.social.user.dto.RefreshTokenRequest;
import com.social.user.dto.SignUpRequest;
import com.social.user.dto.TokenPairResponse;
import com.social.user.dto.UserResponse;
import com.social.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;

    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw BusinessException.conflict("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(request.phone())
                .role(request.role())
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    public TokenPairResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String userId = user.getId().toString();
        String accessToken  = jwtTokenProvider.createAccessToken(userId, user.getRole().name(), user.getName());
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        tokenService.saveRefreshToken(userId, refreshToken,
                jwtTokenProvider.getRefreshTokenExpiration());

        return new TokenPairResponse(accessToken, refreshToken);
    }

    /**
     * 로그아웃:
     *  1) access token 의 jti 를 블랙리스트에 등록 (남은 TTL 동안 유효)
     *  2) Redis 의 refresh token 삭제
     */
    public void logout(String userId, String accessToken) {
        String jti = jwtTokenProvider.getJti(accessToken);
        long remainingMs = jwtTokenProvider.getRemainingExpiryMs(accessToken);
        tokenService.blacklist(jti, remainingMs);
        tokenService.deleteRefreshToken(userId);
    }

    /**
     * 토큰 재발급 (Refresh Token Rotation):
     *  1) refresh token 검증
     *  2) Redis 에 저장된 토큰과 일치 여부 확인 (토큰 탈취 감지)
     *  3) 기존 refresh token 삭제 후 새 토큰 쌍 발급
     */
    public TokenPairResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw BusinessException.unauthorized("유효하지 않은 리프레시 토큰입니다.");
        }

        String userId = jwtTokenProvider.getUserId(refreshToken);
        String stored = tokenService.getRefreshToken(userId);

        if (stored == null || !stored.equals(refreshToken)) {
            // 저장된 토큰과 다르면 탈취 가능성 → 해당 유저의 세션 전체 무효화
            tokenService.deleteRefreshToken(userId);
            throw BusinessException.unauthorized("리프레시 토큰이 만료되었거나 이미 사용되었습니다.");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));

        String newAccessToken  = jwtTokenProvider.createAccessToken(userId, user.getRole().name(), user.getName());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        tokenService.saveRefreshToken(userId, newRefreshToken,
                jwtTokenProvider.getRefreshTokenExpiration());

        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    public UserResponse getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));
    }
}
