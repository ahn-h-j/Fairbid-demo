package com.cos.fairbid.ai.application.port.in;

import java.time.LocalDateTime;

import com.cos.fairbid.ai.domain.guardrail.GuardrailWeeklyReport;

/**
 * 가드레일 주간 리포트 생성 UseCase.
 *
 * 스케줄러가 주기적으로 호출하거나, 테스트/디버깅 시 수동 호출 가능.
 */
public interface GenerateGuardrailReportUseCase {

    /**
     * 지정 기간의 가드레일 실패 데이터를 조회해 리포트 생성 + 전송한다.
     */
    GuardrailWeeklyReport generateAndSend(LocalDateTime from, LocalDateTime to);
}
