package com.cos.fairbid.auth.application.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.application.port.in.LogoutUseCase;
import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase;
import com.cos.fairbid.auth.application.port.in.RefreshTokenUseCase;
import com.cos.fairbid.auth.application.port.out.OAuthClientPort;
import com.cos.fairbid.auth.application.port.out.OAuthUserInfo;
import com.cos.fairbid.auth.application.port.out.RefreshTokenPort;
import com.cos.fairbid.auth.application.port.out.TokenProviderPort;
import com.cos.fairbid.auth.domain.exception.RefreshTokenReusedException;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import com.cos.fairbid.user.domain.UserRole;
import com.cos.fairbid.user.domain.exception.UserBlockedException;
import com.cos.fairbid.user.domain.exception.UserNotFoundException;

/**
 * 인증 서비스
 * OAuth 로그인, 토큰 갱신, 로그아웃을 처리하는 핵심 서비스이다.
 *
 * - login: OAuth Code → 사용자 조회/생성 → 차단 체크 → JWT 발급 → Redis 저장
 * - refresh: Redis 검증 → Token Rotation → 새 토큰 발급
 * - logout: Redis 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements OAuthLoginUseCase, RefreshTokenUseCase, LogoutUseCase {

    private final OAuthClientPort oAuthClientPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final RefreshTokenPort refreshTokenPort;
    private final TokenProviderPort tokenProviderPort;

    @Value("${app.admin.emails:}")
    private String adminEmailsConfig;

    /**
     * ADMIN 이메일 목록을 반환한다.
     * 환경변수에서 콤마로 구분된 이메일 목록을 파싱한다.
     */
    private List<String> getAdminEmails() {
        if (adminEmailsConfig == null || adminEmailsConfig.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(adminEmailsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 이메일 기반으로 사용자 역할을 결정한다.
     * ADMIN_EMAILS 환경변수에 포함된 이메일이면 ADMIN, 아니면 USER.
     * 이메일 비교는 대소문자를 무시한다. (RFC 5321 권장)
     *
     * @param email 사용자 이메일
     * @return UserRole (ADMIN 또는 USER)
     */
    private UserRole determineRole(String email) {
        List<String> adminEmails = getAdminEmails();
        boolean isAdmin = adminEmails.stream()
                .anyMatch(adminEmail -> adminEmail.equalsIgnoreCase(email));
        return isAdmin ? UserRole.ADMIN : UserRole.USER;
    }

    /**
     * OAuth 로그인을 수행한다.
     *
     * 흐름:
     * 1. OAuth Provider에서 사용자 정보 조회
     * 2. loginWithUserInfo() 위임 (사용자 조회/생성 + 토큰 발급 + Redis 저장)
     */
    @Override
    @Transactional
    public LoginResult login(OAuthProvider provider, String code) {
        // 1. OAuth Provider에서 사용자 정보 조회
        OAuthUserInfo userInfo = oAuthClientPort.getUserInfo(provider, code);
        log.debug("OAuth 로그인 시도: provider={}, email={}", provider, maskEmail(userInfo.email()));

        // 2. 사용자 정보 기반 로그인 흐름으로 위임
        return loginWithUserInfo(userInfo);
    }

    /**
     * OAuth 사용자 정보로 로그인을 수행한다.
     *
     * 흐름:
     * 1. DB에서 기존 사용자 조회 (provider + providerId)
     * 2. 없으면 신규 생성
     * 3. 차단 상태 체크
     * 4. JWT 토큰 발급 (Access + Refresh)
     * 5. Refresh Token을 Redis에 저장 (단일 세션: 기존 토큰 덮어씀)
     *
     * 시뮬레이션/테스트 환경에서 OAuth Provider 호출 없이 인증 흐름을 통과시키기 위해 분리되었다.
     */
    @Override
    @Transactional
    public LoginResult loginWithUserInfo(OAuthUserInfo userInfo) {
        OAuthProvider provider = userInfo.provider();

        // 1. 기존 사용자 조회
        boolean isNewUser = false;
        User user = loadUserPort.findByProviderAndProviderId(provider, userInfo.providerId())
                .orElse(null);

        // 2. 신규 사용자 생성 또는 기존 사용자 역할 갱신
        if (user == null) {
            UserRole role = determineRole(userInfo.email());
            user = User.create(userInfo.email(), provider, userInfo.providerId(), role);
            user = saveUserPort.save(user);
            isNewUser = true;
            log.info("신규 사용자 가입: userId={}, provider={}, role={}", user.getId(), provider, role);
        } else {
            // 기존 사용자: ADMIN_EMAILS 변경 시 역할 동기화
            UserRole expectedRole = determineRole(userInfo.email());
            if (user.getRole() != expectedRole) {
                user.updateRole(expectedRole);
                user = saveUserPort.save(user);
                log.info("사용자 역할 변경: userId={}, newRole={}", user.getId(), expectedRole);
            }
        }

        // 3. 차단 상태 체크
        if (user.isBlocked()) {
            if (!user.isActive()) {
                throw UserBlockedException.byDeactivation();
            }
            throw UserBlockedException.byWarningCount();
        }

        // 4. JWT 토큰 발급
        String accessToken = tokenProviderPort.generateAccessToken(user);
        String refreshToken = tokenProviderPort.generateRefreshToken(user);

        // 5. Refresh Token Redis 저장 (단일 세션 정책: 기존 세션 무효화)
        refreshTokenPort.save(user.getId(), refreshToken, tokenProviderPort.getRefreshExpirationSeconds());

        return new LoginResult(user, accessToken, refreshToken, isNewUser);
    }

    /**
     * 토큰을 갱신한다. (Token Rotation 적용)
     *
     * 흐름:
     * 1. Refresh Token에서 userId 추출 (유효성 검증 포함)
     * 2. Redis에 저장된 토큰과 일치하는지 확인 (재사용 감지)
     * 3. 사용자 조회
     * 4. 새 토큰 발급 (Access + Refresh)
     * 5. Redis에 새 Refresh Token 저장 (기존 토큰 무효화)
     */
    @Override
    public TokenResult refresh(String refreshToken) {
        // 1. Refresh Token 유효성 검증 + userId 추출
        Long userId = tokenProviderPort.getUserIdFromRefreshToken(refreshToken);

        // 2. Redis에 저장된 토큰과 일치하는지 확인 (Token Rotation 재사용 감지)
        if (!refreshTokenPort.matches(userId, refreshToken)) {
            // 재사용 감지: 탈취 가능성 → 해당 사용자의 모든 세션 무효화
            refreshTokenPort.delete(userId);
            log.warn("Refresh Token 재사용 감지! userId={}", userId);
            throw RefreshTokenReusedException.detected(userId);
        }

        // 3. 사용자 조회
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // 4. 새 토큰 발급
        String newAccessToken = tokenProviderPort.generateAccessToken(user);
        String newRefreshToken = tokenProviderPort.generateRefreshToken(user);

        // 5. Redis 갱신 (Token Rotation: 이전 토큰 무효화)
        refreshTokenPort.save(userId, newRefreshToken, tokenProviderPort.getRefreshExpirationSeconds());

        return new TokenResult(newAccessToken, newRefreshToken, user.isOnboarded());
    }

    /**
     * 로그아웃을 수행한다.
     * Redis에서 Refresh Token을 삭제하여 해당 세션을 무효화한다.
     */
    @Override
    public void logout(Long userId) {
        refreshTokenPort.delete(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }

    /**
     * 이메일을 마스킹한다. (PII 보호)
     * 예: "user@example.com" → "u***@example.com"
     *
     * @param email 원본 이메일
     * @return 마스킹된 이메일
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
