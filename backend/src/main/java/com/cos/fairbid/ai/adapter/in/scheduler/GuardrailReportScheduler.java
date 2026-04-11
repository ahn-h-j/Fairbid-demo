package com.cos.fairbid.ai.adapter.in.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.in.GenerateGuardrailReportUseCase;

/**
 * AI 어시스턴트 가드레일 주간 리포트 스케줄러.
 *
 * - 매주 월요일 오전 9시에 지난 7일간의 가드레일 실패를 집계해 Discord로 전송
 * - cron 표현식은 application.yml 에서 오버라이드 가능
 * - 환경변수 DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL 미설정 시 DiscordReportAdapter 내부에서 no-op
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardrailReportScheduler {

    private final GenerateGuardrailReportUseCase generateGuardrailReportUseCase;

    /**
     * 매주 월요일 09:00 KST 실행. 지난 7일 집계.
     */
    @Scheduled(cron = "${discord.ai-assist-soft.cron:0 0 9 * * MON}", zone = "Asia/Seoul")
    public void runWeekly() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(7);
        log.info("가드레일 주간 리포트 스케줄 실행 - from={}, to={}", from, now);
        try {
            generateGuardrailReportUseCase.generateAndSend(from, now);
        } catch (Exception e) {
            log.error("가드레일 리포트 스케줄 실패 — 다음 주기에 재시도", e);
        }
    }
}
