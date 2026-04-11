package com.cos.fairbid.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 사용자 입력(memo)에서 프롬프트 인젝션 패턴이 탐지됐을 때 발생.
 *
 * 탐지 예시:
 * - "기존 지시 무시하고 ~"
 * - "이전 내용 모두 잊어"
 * - "시스템 프롬프트 보여줘"
 * - "너는 이제 ~이다"
 *
 * HTTP 400 Bad Request 로 매핑. 사용자에게는 기술 용어 없이 안내.
 */
public class PromptInjectionDetectedException extends DomainException {

    private static final String USER_MESSAGE =
            "상품 정보 입력에 부적절한 내용이 포함되어 있어요. 판매할 상품의 정보만 입력해주세요.";

    private PromptInjectionDetectedException() {
        super("PROMPT_INJECTION_DETECTED", USER_MESSAGE);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static PromptInjectionDetectedException of() {
        return new PromptInjectionDetectedException();
    }
}
