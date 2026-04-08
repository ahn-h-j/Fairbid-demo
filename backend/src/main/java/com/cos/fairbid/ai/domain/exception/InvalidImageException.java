package com.cos.fairbid.ai.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * AI가 이미지 URL을 분석할 수 없을 때 발생.
 * - 이미지 URL 접근 불가 (404, DNS 실패)
 * - 지원하지 않는 이미지 포맷
 * - Claude vision 입력 검증 실패
 *
 * HTTP 400 Bad Request 에 매핑.
 *
 * 내부 실패 사유는 호출 사이트에서 로그로 남기고, 사용자에게는 친화적 메시지만 노출한다.
 */
public class InvalidImageException extends DomainException {

    private static final String USER_MESSAGE =
            "이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요.";

    private InvalidImageException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static InvalidImageException of() {
        return new InvalidImageException("INVALID_IMAGE", USER_MESSAGE);
    }
}
