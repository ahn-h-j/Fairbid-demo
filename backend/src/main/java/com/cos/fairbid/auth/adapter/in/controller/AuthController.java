package com.cos.fairbid.auth.adapter.in.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.adapter.in.dto.TokenResponse;
import com.cos.fairbid.auth.application.port.in.GuestLoginUseCase;
import com.cos.fairbid.auth.application.port.in.LogoutUseCase;
import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase;
import com.cos.fairbid.auth.application.port.in.RefreshTokenUseCase;
import com.cos.fairbid.auth.infrastructure.security.CookieUtils;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.user.domain.OAuthProvider;

/**
 * 인증 컨트롤러
 *
 * OAuth2 로그인 흐름:
 * 1. GET /api/v1/auth/oauth2/{provider} → Provider 인증 페이지로 리다이렉트
 * 2. GET /api/v1/auth/oauth2/callback/{provider} → 콜백 처리 → Refresh 쿠키 설정 → 프론트로 리다이렉트
 * 3. POST /api/v1/auth/refresh → Access Token 재발급 (프론트가 콜백 후 호출)
 * 4. POST /api/v1/auth/logout → Refresh Token 삭제 + 쿠키 제거
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String OAUTH_STATE_COOKIE = "oauth_state";
    private static final int OAUTH_STATE_MAX_AGE = 300; // 5분 (초)

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GuestLoginUseCase guestLoginUseCase;
    private final CookieUtils cookieUtils;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${oauth2.kakao.client-id:}")
    private String kakaoClientId;

    @Value("${oauth2.kakao.redirect-uri:}")
    private String kakaoRedirectUri;

    @Value("${oauth2.naver.client-id:}")
    private String naverClientId;

    @Value("${oauth2.naver.redirect-uri:}")
    private String naverRedirectUri;

    @Value("${oauth2.google.client-id:}")
    private String googleClientId;

    @Value("${oauth2.google.redirect-uri:}")
    private String googleRedirectUri;

    /**
     * OAuth Provider 인증 페이지로 리다이렉트한다.
     * CSRF 방지를 위해 랜덤 state를 생성하고 쿠키에 저장한 후
     * Authorization URL에 포함하여 전달한다.
     *
     * @param provider OAuth Provider (kakao, naver, google)
     */
    @GetMapping("/oauth2/{provider}")
    public void redirectToProvider(@PathVariable String provider, HttpServletResponse response) throws Exception {
        OAuthProvider oAuthProvider = parseProvider(provider);

        // CSRF 방지: 랜덤 state 생성 → 쿠키 저장 → Auth URL에 포함
        String state = UUID.randomUUID().toString();
        setOAuthStateCookie(response, state);

        String authorizationUrl = buildAuthorizationUrl(oAuthProvider, state);
        response.sendRedirect(authorizationUrl);
    }

    /**
     * OAuth 콜백을 처리한다.
     * Provider에서 Authorization Code를 받아 로그인/가입 처리 후
     * Refresh Token을 HttpOnly 쿠키에 설정하고 프론트엔드로 리다이렉트한다.
     *
     * state 파라미터를 쿠키에 저장된 값과 비교하여 CSRF 공격을 방지한다.
     *
     * @param provider OAuth Provider (kakao, naver, google)
     * @param code     Authorization Code
     * @param state    CSRF 방지 state 파라미터
     */
    @GetMapping("/oauth2/callback/{provider}")
    public void handleCallback(@PathVariable String provider,
                               @RequestParam String code,
                               @RequestParam(required = false) String state,
                               HttpServletRequest request,
                               HttpServletResponse response) throws Exception {
        OAuthProvider oAuthProvider = parseProvider(provider);

        // CSRF 방지: state 검증
        String storedState = extractStateCookie(request);
        if (storedState == null || !storedState.equals(state)) {
            log.warn("OAuth state 불일치: provider={}", provider);
            response.sendRedirect(frontendUrl + "/auth/error?reason=invalid_state");
            return;
        }
        // state 쿠키 제거 (일회용)
        clearOAuthStateCookie(response);

        // OAuth 로그인 처리
        OAuthLoginUseCase.LoginResult result = oAuthLoginUseCase.login(oAuthProvider, code);

        // Refresh Token을 HttpOnly 쿠키에 설정
        cookieUtils.setRefreshTokenCookie(response, result.refreshToken());

        // 프론트엔드 콜백 페이지로 리다이렉트
        // 프론트에서 /auth/callback 로드 → POST /api/v1/auth/refresh 호출 → Access Token 수신
        String redirectUrl = frontendUrl + "/auth/callback";
        response.sendRedirect(redirectUrl);
    }

    /**
     * 게스트 체험(데모) 로그인을 수행한다.
     *
     * 소셜 로그인 없이 임시 데모 계정을 즉석 발급한다. (이력서/포트폴리오 데모용)
     * OAuth 콜백(리다이렉트)과 달리 프론트가 직접 POST 호출하므로, 응답 본문으로 Access Token을 바로 내려준다.
     * Refresh Token은 OAuth 흐름과 동일하게 HttpOnly 쿠키로 설정한다.
     */
    @PostMapping("/demo-login")
    public ResponseEntity<ApiResponse<TokenResponse>> demoLogin(HttpServletResponse response) {
        OAuthLoginUseCase.LoginResult result = guestLoginUseCase.guestLogin();

        // Refresh Token은 HttpOnly 쿠키로 (OAuth 흐름과 동일)
        cookieUtils.setRefreshTokenCookie(response, result.refreshToken());

        // 게스트는 온보딩 자동 완료 상태이므로 onboarded=true로 응답
        return ResponseEntity.ok(ApiResponse.success(
                new TokenResponse(result.accessToken(), result.user().isOnboarded())));
    }

    /**
     * Access Token을 갱신한다.
     * Refresh Token 쿠키를 사용하여 새로운 Access Token을 발급한다.
     * Token Rotation: Refresh Token도 새로 발급되어 쿠키에 재설정된다.
     *
     * @param refreshToken 쿠키에서 전달된 Refresh Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        try {
            // 토큰 갱신 (Token Rotation 적용)
            RefreshTokenUseCase.TokenResult result = refreshTokenUseCase.refresh(refreshToken);

            // 새 Refresh Token 쿠키 설정
            cookieUtils.setRefreshTokenCookie(response, result.newRefreshToken());

            // Access Token + onboarded 정보 응답 (ApiResponse로 래핑)
            return ResponseEntity.ok(ApiResponse.success(new TokenResponse(result.accessToken(), result.onboarded())));
        } catch (Exception e) {
            // 토큰 갱신 실패 시 쿠키 제거 → 브라우저가 만료된 토큰으로 재시도하는 무한 루프 방지
            cookieUtils.clearRefreshTokenCookie(response);
            throw e;
        }
    }

    /**
     * 로그아웃을 수행한다.
     * Redis에서 Refresh Token을 삭제하고 쿠키를 제거한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        logoutUseCase.logout(userId);

        // Refresh Token 쿠키 제거
        cookieUtils.clearRefreshTokenCookie(response);

        return ResponseEntity.ok().build();
    }

    /**
     * OAuth state를 HttpOnly 쿠키에 저장한다. (CSRF 방지)
     * 짧은 TTL(5분)로 설정하여 만료된 state를 자동 제거한다.
     * Secure 플래그는 환경변수(app.cookie.secure)에 의해 제어된다.
     */
    private void setOAuthStateCookie(HttpServletResponse response, String state) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(OAUTH_STATE_COOKIE).append("=").append(state)
                .append("; Path=/api/v1/auth/oauth2")
                .append("; Max-Age=").append(OAUTH_STATE_MAX_AGE)
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (cookieSecure) {
            cookie.append("; Secure");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * OAuth state 쿠키를 제거한다.
     */
    private void clearOAuthStateCookie(HttpServletResponse response) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(OAUTH_STATE_COOKIE).append("=")
                .append("; Path=/api/v1/auth/oauth2")
                .append("; Max-Age=0")
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (cookieSecure) {
            cookie.append("; Secure");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * 요청의 쿠키에서 OAuth state 값을 추출한다.
     *
     * @param request HTTP 요청
     * @return state 값 (없으면 null)
     */
    private String extractStateCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (OAUTH_STATE_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * provider 문자열을 OAuthProvider enum으로 파싱한다.
     * 유효하지 않은 provider면 IllegalArgumentException을 던진다.
     *
     * @param provider 소문자 provider 문자열
     * @return OAuthProvider enum 값
     * @throws IllegalArgumentException 지원하지 않는 provider
     */
    private OAuthProvider parseProvider(String provider) {
        try {
            return OAuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 OAuth Provider입니다: " + provider);
        }
    }

    /**
     * OAuth Provider별 인증 URL을 생성한다.
     * CSRF 방지를 위해 state 파라미터를 포함한다.
     *
     * @param provider OAuthProvider enum
     * @param state    CSRF 방지 state 값
     * @return 인증 URL
     */
    private String buildAuthorizationUrl(OAuthProvider provider, String state) {
        return switch (provider) {
            case KAKAO -> "https://kauth.kakao.com/oauth/authorize"
                    + "?client_id=" + kakaoClientId
                    + "&redirect_uri=" + kakaoRedirectUri
                    + "&response_type=code"
                    + "&scope=account_email"
                    + "&state=" + state;
            case NAVER -> "https://nid.naver.com/oauth2.0/authorize"
                    + "?client_id=" + naverClientId
                    + "&redirect_uri=" + naverRedirectUri
                    + "&response_type=code"
                    + "&state=" + state;
            case GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth"
                    + "?client_id=" + googleClientId
                    + "&redirect_uri=" + googleRedirectUri
                    + "&response_type=code"
                    + "&scope=email profile"
                    + "&state=" + state;
            // DEMO(게스트)는 외부 인증 페이지가 없다. (별도 /demo-login 엔드포인트 사용)
            case DEMO -> throw new IllegalArgumentException("DEMO provider는 인증 페이지 리다이렉트를 지원하지 않습니다.");
        };
    }
}
