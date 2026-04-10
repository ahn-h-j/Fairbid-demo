package com.cos.fairbid.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * AI 서비스(Claude API)를 일시적으로 사용할 수 없을 때 발생.
 * - Claude API 5xx 응답
 * - 네트워크 타임아웃
 * - 인증 실패 등
 *
 * HTTP 503 Service Unavailable 에 매핑.
 */
public class AiServiceUnavailableException extends DomainException {

    private AiServiceUnavailableException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    /**
     * 일반적인 AI 서비스 장애.
     */
    public static AiServiceUnavailableException of() {
        return new AiServiceUnavailableException(
                "AI_SERVICE_UNAVAILABLE",
                "AI 서비스를 일시적으로 사용할 수 없습니다."
        );
    }

    /**
     * 원인 예외를 포함한 장애.
     */
    public static AiServiceUnavailableException withCause(Throwable cause) {
        AiServiceUnavailableException exception = of();
        exception.initCause(cause);
        return exception;
    }
}
