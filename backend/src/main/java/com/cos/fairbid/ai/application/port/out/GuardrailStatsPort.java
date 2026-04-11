package com.cos.fairbid.ai.application.port.out;

import java.time.LocalDateTime;

import com.cos.fairbid.ai.domain.guardrail.GuardrailWeeklyReport;

/**
 * 가드레일 실패 통계 조회 포트.
 *
 * 주간 리포트 생성 시 DB에서 집계 데이터를 가져오는 용도.
 */
public interface GuardrailStatsPort {

    /**
     * 지정 기간의 가드레일 실패를 집계해 주간 리포트로 반환한다.
     */
    GuardrailWeeklyReport buildReport(LocalDateTime from, LocalDateTime to);
}
