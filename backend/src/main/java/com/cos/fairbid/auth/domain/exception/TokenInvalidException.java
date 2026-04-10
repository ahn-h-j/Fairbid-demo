package com.cos.fairbid.auth.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * JWT 토큰 유효하지 않음 예외
 * 서명 불일치, 형식 오류 등 토큰 자체가 유효하지 않을 때 발생한다.
 * HTTP 401 Unauthorized에 매핑된다.
 */
public class TokenInvalidException extends DomainException {

    private TokenInvalidException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    /**
     * 토큰 형식이 잘못되었거나 서명이 유효하지 않을 때 사용한다.
     */
    public static TokenInvalidException malformed() {
        return new TokenInvalidException("TOKEN_INVALID", "유효하지 않은 토큰입니다.");
    }
}
