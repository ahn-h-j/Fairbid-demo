package com.cos.fairbid.ai.application.port.in;

/**
 * AI 경매 어시스턴트 사용 한도 유스케이스.
 *
 * 데모 환경의 LLM API 비용 방어용. 컨트롤러가 AI 추천 호출 "전"에 한도를 검사하고,
 * 추천이 정상 출력으로 "성공"한 직후에만 사용 횟수를 적립한다.
 * (실패한 호출은 적립하지 않으므로 일시적 오류로 한도가 소진되지 않는다.)
 */
public interface AiUsageLimitUseCase {

    /**
     * 현재 사용자가 AI 추천을 호출할 수 있는 상태인지 검사한다.
     * 사용자별 또는 전역 일일 한도를 초과했으면 {@code AiRateLimitExceededException}을 던진다.
     *
     * @param userId 호출 사용자 ID
     */
    void ensureWithinLimit(Long userId);

    /**
     * AI 추천이 정상 출력으로 성공했을 때 사용 횟수를 1 증가시킨다.
     * 반드시 추천 호출이 예외 없이 끝난 뒤에만 호출해야 한다.
     *
     * @param userId 호출 사용자 ID
     */
    void recordSuccess(Long userId);
}
