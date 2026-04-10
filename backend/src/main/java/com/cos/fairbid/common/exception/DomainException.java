package com.cos.fairbid.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 도메인 예외 베이스 클래스
 * 모든 비즈니스 도메인 예외가 상속받는 추상 클래스
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * HTTP 상태 코드를 반환한다
     * 각 예외 클래스에서 오버라이드하여 적절한 상태 코드 지정
     *
     * @return HTTP 상태 코드
     */
    public abstract HttpStatus getStatus();
}
