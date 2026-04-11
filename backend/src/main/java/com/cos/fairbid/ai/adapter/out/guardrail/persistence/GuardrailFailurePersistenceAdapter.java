package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.out.GuardrailFailurePort;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 가드레일 실패 기록 Persistence Adapter.
 *
 * 위반 항목마다 별도 행으로 저장한다.
 * /evolve 에서 rule_id + category 로 GROUP BY 해서 패턴을 분석한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardrailFailurePersistenceAdapter implements GuardrailFailurePort {

    private final GuardrailFailureRepository repository;

    @Override
    public void save(
            List<GuardrailViolation> violations,
            String category,
            String keyword,
            Long aiMidPrice,
            Long searchMedian,
            int attemptCount
    ) {
        for (GuardrailViolation violation : violations) {
            try {
                GuardrailFailureEntity entity = GuardrailFailureEntity.builder()
                        .ruleId(violation.ruleId())
                        .severity(violation.severity().name())
                        .category(category)
                        .keyword(keyword)
                        .violationMessage(violation.message())
                        .aiMidPrice(aiMidPrice)
                        .searchMedianPrice(searchMedian)
                        .attemptCount(attemptCount)
                        .build();
                repository.save(entity);
            } catch (Exception e) {
                // 실패 기록 저장 자체가 실패해도 본 흐름을 막지 않는다
                log.warn("가드레일 실패 기록 저장 실패 - ruleId={}, error={}",
                        violation.ruleId(), e.getMessage());
            }
        }

        log.info("가드레일 실패 기록 저장 - count={}, category={}, keyword={}",
                violations.size(), category, keyword);
    }
}
