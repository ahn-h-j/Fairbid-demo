package com.cos.fairbid.ai.application.port.out;

/**
 * AI 사용 횟수 카운터 저장소 포트.
 *
 * 사용자별·전역 일일 호출 횟수를 외부 저장소(Redis)에 보관한다.
 * 구현체는 저장소 장애 시 데모 동작이 멈추지 않도록 fail-open(허용)으로 동작해야 한다.
 */
public interface AiUsageLimitPort {

    /**
     * 오늘 해당 사용자의 누적 성공 호출 횟수를 반환한다.
     *
     * @param userId 사용자 ID
     * @return 오늘 누적 성공 횟수 (조회 실패 시 0)
     */
    long getUserDailyCount(Long userId);

    /**
     * 오늘 전역(서비스 전체) 누적 성공 호출 횟수를 반환한다.
     *
     * @return 오늘 전역 누적 성공 횟수 (조회 실패 시 0)
     */
    long getGlobalDailyCount();

    /**
     * 해당 사용자와 전역 카운터를 각각 1 증가시킨다. (성공한 호출 1건 적립)
     *
     * @param userId 사용자 ID
     */
    void increment(Long userId);
}
