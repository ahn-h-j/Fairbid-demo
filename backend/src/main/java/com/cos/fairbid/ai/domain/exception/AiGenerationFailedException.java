package com.cos.fairbid.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * AI 응답 본문 파싱/스키마 검증에 실패했을 때 발생.
 * - Claude 응답 JSON 파싱 실패
 * - 필수 필드(suggestedPrices, generatedDescription) 누락
 * - low/mid/high 타입 불일치 등
 *
 * HTTP 502 Bad Gateway 에 매핑 (외부 서비스 응답이 비정상이라는 의미).
 *
 * 내부 실패 사유(JSON 파싱 실패, 필드 누락 등)는 호출 사이트에서 로그로 남기고,
 * 사용자에게는 기술 용어 없는 친화적 메시지만 노출한다.
 */
public class AiGenerationFailedException extends DomainException {

    private static final String USER_MESSAGE =
            "AI가 추천을 생성하지 못했어요. 잠시 후 다시 시도하거나, 상품 정보를 조금 더 자세히 입력해주세요.";

    private AiGenerationFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        // 422: AI가 요청은 이해했으나(이미지-설명 불일치 등) 추천을 생성하지 못한 비즈니스 케이스.
        // 502 로 두면 Cloudflare/프록시가 자체 502 페이지로 덮어 사용자 친화 안내 메시지가 가려진다.
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public static AiGenerationFailedException of() {
        return new AiGenerationFailedException("AI_GENERATION_FAILED", USER_MESSAGE);
    }

    public static AiGenerationFailedException withCause(Throwable cause) {
        AiGenerationFailedException exception = of();
        exception.initCause(cause);
        return exception;
    }

    /**
     * Claude 가 응답에 직접 작성한 사용자 안내 메시지를 그대로 노출할 때 사용.
     * status != "success" 분기에서 사용된다.
     *
     * @param claudeUserMessage Claude 가 `userMessage` 필드에 써준 한국어 안내문
     */
    public static AiGenerationFailedException fromAi(String claudeUserMessage) {
        return new AiGenerationFailedException("AI_GENERATION_FAILED", claudeUserMessage);
    }
}
