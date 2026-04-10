package com.cos.fairbid.auth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Refresh Token 쿠키 관리 컴포넌트
 * AuthController와 UserController에서 공용으로 사용한다.
 *
 * 쿠키 보안 설정:
 * - HttpOnly: JavaScript 접근 차단 (XSS 방어)
 * - Secure: HTTPS에서만 전송 (환경변수로 제어, 로컬 HTTP 환경에서는 비활성화)
 * - SameSite=Lax: OAuth 리다이렉트 호환 (Strict는 cross-site 리다이렉트 시 쿠키 누락)
 * - Path=/api/v1/auth: 인증 엔드포인트에서만 전송
 */
@Component
public class CookieUtils {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final int REFRESH_TOKEN_MAX_AGE = 14 * 24 * 60 * 60; // 2주 (초)

    private final boolean secure;

    public CookieUtils(@Value("${app.cookie.secure:true}") boolean secure) {
        // COOKIE_SECURE 환경변수 또는 app.cookie.secure 프로퍼티로 제어
        this.secure = secure;
    }

    /**
     * Refresh Token을 HttpOnly 쿠키에 설정한다.
     * Secure 플래그는 환경변수(COOKIE_SECURE)에 의해 제어된다.
     *
     * @param response     HTTP 응답
     * @param refreshToken Refresh Token 값
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(REFRESH_TOKEN_COOKIE).append("=").append(refreshToken)
                .append("; Path=/api/v1/auth")
                .append("; Max-Age=").append(REFRESH_TOKEN_MAX_AGE)
                .append("; HttpOnly")
                .append("; SameSite=Lax");

        if (secure) {
            cookie.append("; Secure");
        }

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Refresh Token 쿠키를 제거한다.
     *
     * @param response HTTP 응답
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(REFRESH_TOKEN_COOKIE).append("=")
                .append("; Path=/api/v1/auth")
                .append("; Max-Age=0")
                .append("; HttpOnly")
                .append("; SameSite=Lax");

        if (secure) {
            cookie.append("; Secure");
        }

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
