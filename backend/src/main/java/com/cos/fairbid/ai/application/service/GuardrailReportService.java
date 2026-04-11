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
 * 흐름:
 *   1. GuardrailStatsPort 로 DB 집계 조회
 *   2. GuardrailReportPort 로 전송 (Discord 등)
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
        GuardrailWeeklyReport report = guardrailStatsPort.buildReport(from, to);
        guardrailReportPort.sendWeeklyReport(report);
        log.info("가드레일 리포트 전송 완료 - total={}", report.totalViolations());
        return report;
    }
}
