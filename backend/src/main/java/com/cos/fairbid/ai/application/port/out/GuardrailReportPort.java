package com.cos.fairbid.ai.application.port.out;

import com.cos.fairbid.ai.domain.guardrail.GuardrailWeeklyReport;

/**
 * 가드레일 리포트 전송 포트.
 *
 * 현재 구현체: DiscordReportAdapter (Discord Webhook)
 * 다른 채널(Slack, Email 등)로 확장 가능.
 */
public interface GuardrailReportPort {

    /**
     * 주간 리포트를 외부 채널로 전송한다.
     * 실패해도 예외를 throw 하지 않는다 (로그만 남김).
     */
    void sendWeeklyReport(GuardrailWeeklyReport report);
}
