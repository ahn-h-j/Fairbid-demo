package com.cos.fairbid.ai.adapter.out.discord;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.out.GuardrailReportPort;
import com.cos.fairbid.ai.domain.guardrail.GuardrailWeeklyReport;

/**
 * Discord Webhook 으로 가드레일 주간 리포트를 전송한다.
 *
 * - Webhook URL 미설정 시 no-op (로그만 찍고 종료)
 * - 네트워크 실패는 경고만 남기고 throw 하지 않는다 (스케줄러 흐름 보호)
 * - Discord content 2000자 제한에 맞춰 메시지 길이 절단
 */
@Slf4j
@Component
public class DiscordReportAdapter implements GuardrailReportPort {

    private static final int DISCORD_CONTENT_MAX = 1900; // 여유 두고 1900
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DiscordProperties properties;
    private final RestClient restClient;

    public DiscordReportAdapter(DiscordProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void sendWeeklyReport(GuardrailWeeklyReport report) {
        if (!properties.isEnabled()) {
            log.info("Discord Webhook URL 미설정 — 리포트 전송 건너뜀");
            return;
        }

        String content = formatContent(report);

        try {
            restClient.post()
                    .uri(properties.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Discord 리포트 전송 완료");
        } catch (RestClientResponseException e) {
            log.warn("Discord Webhook HTTP 에러 - status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.warn("Discord Webhook 네트워크 오류: {}", e.getMessage());
        }
    }

    /**
     * 리포트를 Discord markdown 형식으로 포맷팅한다.
     * 2000자 제한에 걸리지 않게 잘라낸다.
     */
    private String formatContent(GuardrailWeeklyReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **AI 가드레일 주간 리포트**\n");
        sb.append("기간: `").append(report.from().format(DATE_FMT))
          .append("` ~ `").append(report.to().format(DATE_FMT)).append("`\n");
        sb.append("총 위반: **").append(report.totalViolations()).append("건**\n\n");

        if (report.totalViolations() == 0) {
            sb.append("_위반 없음 ✨_");
            return truncate(sb.toString());
        }

        // rule_id 별 카운트
        sb.append("**규칙별 집계**\n");
        for (GuardrailWeeklyReport.RuleCount rc : report.byRule()) {
            sb.append("- `").append(rc.ruleId()).append("`: ")
              .append(rc.count()).append("건\n");
        }

        // rule + category 조합 상위
        if (!report.byRuleCategory().isEmpty()) {
            sb.append("\n**규칙 × 카테고리 Top**\n");
            for (GuardrailWeeklyReport.RuleCategoryCount rcc : report.byRuleCategory()) {
                sb.append("- `").append(rcc.ruleId()).append("` × ")
                  .append(rcc.category() != null ? rcc.category() : "(none)")
                  .append(": ").append(rcc.count()).append("건\n");
            }
        }

        // 샘플 메시지
        if (!report.topMessages().isEmpty()) {
            sb.append("\n**반복 위반 샘플**\n");
            for (GuardrailWeeklyReport.RuleSample sample : report.topMessages()) {
                sb.append("▸ **").append(sample.ruleId()).append("**\n");
                for (String msg : sample.messages()) {
                    String truncated = msg.length() > 100 ? msg.substring(0, 100) + "…" : msg;
                    sb.append("  - ").append(truncated).append("\n");
                }
            }
        }

        sb.append("\n_`/evolve` 로 규칙 개선 제안 확인_");
        return truncate(sb.toString());
    }

    private String truncate(String content) {
        if (content.length() <= DISCORD_CONTENT_MAX) {
            return content;
        }
        return content.substring(0, DISCORD_CONTENT_MAX - 20) + "\n… (truncated)";
    }
}
