package com.cos.fairbid.ai.application.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.in.GenerateGuardrailReportUseCase;
import com.cos.fairbid.ai.application.port.out.GuardrailReportPort;
import com.cos.fairbid.ai.application.port.out.GuardrailStatsPort;
import com.cos.fairbid.ai.domain.guardrail.GuardrailWeeklyReport;

/**
 * 가드레일 리포트 생성 + 전송 UseCase 구현.
 *
 * 트랜잭션 설계:
 *   - DB 집계는 {@link GuardrailStatsPort} 구현체(어댑터) 내부에서 readOnly 트랜잭션으로 처리
 *   - 외부 I/O (Discord 전송) 는 트랜잭션 밖에서 실행되어 DB 커넥션 점유를 최소화
 *   - 전송 실패는 어댑터 내부에서 흡수되어 본 흐름을 막지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailReportService implements GenerateGuardrailReportUseCase {

    private final GuardrailStatsPort guardrailStatsPort;
    private final GuardrailReportPort guardrailReportPort;

    @Override
    public GuardrailWeeklyReport generateAndSend(LocalDateTime from, LocalDateTime to) {
        log.info("가드레일 리포트 생성 시작 - period={} ~ {}", from, to);

        // 1. DB 집계 조회 — 어댑터 내부에서 readOnly 트랜잭션
        GuardrailWeeklyReport report = guardrailStatsPort.buildReport(from, to);

        // 2. 외부 HTTP 전송 — 트랜잭션 밖
        guardrailReportPort.sendWeeklyReport(report);

        log.info("가드레일 리포트 전송 완료 - total={}", report.totalViolations());
        return report;
    }
}
