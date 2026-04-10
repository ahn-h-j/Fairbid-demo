package com.cos.fairbid.auth.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * JWT 토큰 만료 예외
 * Access Token 또는 Refresh Token이 만료되었을 때 발생한다.
 * HTTP 401 Unauthorized에 매핑된다.
 */
public class TokenExpiredException extends DomainException {

    private TokenExpiredException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    /**
     * Access Token 만료 시 사용한다.
     */
    public static TokenExpiredException accessToken() {
        return new TokenExpiredException("ACCESS_TOKEN_EXPIRED", "Access Token이 만료되었습니다.");
    }

    /**
     * Refresh Token 만료 시 사용한다.
     */
    public static TokenExpiredException refreshToken() {
        return new TokenExpiredException("REFRESH_TOKEN_EXPIRED", "Refresh Token이 만료되었습니다. 다시 로그인해주세요.");
    }
}
