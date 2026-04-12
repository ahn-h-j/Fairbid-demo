package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.out.GuardrailStatsPort;
import com.cos.fairbid.ai.domain.guardrail.GuardrailWeeklyReport;

/**
 * 가드레일 실패 통계 조회 Persistence Adapter.
 * GuardrailFailureRepository 의 집계 쿼리를 사용해 주간 리포트를 조립한다.
 *
 * readOnly 트랜잭션 — 네 개의 집계 쿼리를 하나의 연결로 실행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardrailStatsPersistenceAdapter implements GuardrailStatsPort {

    /** 상위 몇 개 규칙에 대해 샘플 메시지를 수집할지 */
    private static final int SAMPLE_TOP_N_RULES = 3;
    /** 규칙당 샘플 메시지 개수 */
    private static final int SAMPLE_MESSAGES_PER_RULE = 5;
    /** rule + category 조합 상위 몇 개까지 리포트에 포함할지 */
    private static final int TOP_CATEGORY_COMBINATIONS = 10;

    private final GuardrailFailureRepository repository;

    @Override
    public GuardrailWeeklyReport buildReport(LocalDateTime from, LocalDateTime to) {
        long total = repository.countInPeriod(from, to);

        List<RuleCount> byRuleRaw = repository.countByRuleInPeriod(from, to);
        List<GuardrailWeeklyReport.RuleCount> byRule = byRuleRaw.stream()
                .map(r -> new GuardrailWeeklyReport.RuleCount(r.ruleId(), r.count()))
                .toList();

        List<RuleCategoryCount> byRuleCategoryRaw = repository.countByRuleAndCategoryInPeriod(from, to);
        List<GuardrailWeeklyReport.RuleCategoryCount> byRuleCategory = byRuleCategoryRaw.stream()
                .limit(TOP_CATEGORY_COMBINATIONS)
                .map(r -> new GuardrailWeeklyReport.RuleCategoryCount(r.ruleId(), r.category(), r.count()))
                .toList();

        // 상위 N개 규칙에 대해 샘플 메시지 수집
        List<GuardrailWeeklyReport.RuleSample> samples = new ArrayList<>();
        for (int i = 0; i < Math.min(SAMPLE_TOP_N_RULES, byRuleRaw.size()); i++) {
            String ruleId = byRuleRaw.get(i).ruleId();
            List<String> messages = repository.recentMessages(
                    ruleId, from, to, PageRequest.of(0, SAMPLE_MESSAGES_PER_RULE));
            samples.add(new GuardrailWeeklyReport.RuleSample(ruleId, messages));
        }

        log.info("가드레일 리포트 조회 완료 - total={}, rules={}, period={} ~ {}",
                total, byRule.size(), from, to);

        return new GuardrailWeeklyReport(from, to, total, byRule, byRuleCategory, samples);
    }
}
