package com.cos.fairbid.ai.domain.guardrail;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 가드레일 주간 리포트 도메인 객체.
 *
 * @param from           기간 시작 (포함)
 * @param to             기간 끝 (미포함)
 * @param totalViolations 기간 내 총 위반 수
 * @param byRule         rule_id 별 카운트 (내림차순)
 * @param byRuleCategory rule_id + category 조합별 카운트 (상위 10)
 * @param topMessages    상위 규칙의 최근 위반 메시지 샘플
 */
public record GuardrailWeeklyReport(
        LocalDateTime from,
        LocalDateTime to,
        long totalViolations,
        List<RuleCount> byRule,
        List<RuleCategoryCount> byRuleCategory,
        List<RuleSample> topMessages
) {
    public record RuleCount(String ruleId, long count) {
    }

    public record RuleCategoryCount(String ruleId, String category, long count) {
    }

    public record RuleSample(String ruleId, List<String> messages) {
    }
}
