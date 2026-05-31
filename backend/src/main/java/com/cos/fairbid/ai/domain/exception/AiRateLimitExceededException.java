package com.cos.fairbid.ai.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * AI 경매 어시스턴트 호출 한도 초과 예외.
 *
 * 데모/포트폴리오 환경에서 외부 LLM(Claude/Gemini) API 비용이 무한정 발생하는 것을 막기 위해
 * 사용자별·전역 일일 호출 한도를 두고, 초과 시 이 예외를 던진다.
 *
 * 한도는 "정상 출력에 성공한 호출"만 카운트한다. 실패한 호출은 카운트하지 않으므로
 * 일시적 오류로 한도가 소진되지 않고 재시도할 수 있다.
 */
public class AiRateLimitExceededException extends DomainException {

    public AiRateLimitExceededException(String message) {
        super("AI_RATE_LIMIT_EXCEEDED", message);
    }

    /** 사용자별 일일 한도 초과. */
    public static AiRateLimitExceededException forUser() {
        return new AiRateLimitExceededException(
                "오늘 AI 추천 사용 횟수를 모두 사용했어요. 내일 다시 이용해주세요.");
    }

    /** 전역(서비스 전체) 일일 한도 초과. */
    public static AiRateLimitExceededException forGlobal() {
        return new AiRateLimitExceededException(
                "지금은 AI 추천 요청이 많아 잠시 이용할 수 없어요. 잠시 후 다시 시도해주세요.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
