package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.out.GuardrailFailurePort;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 가드레일 실패 기록 Persistence Adapter.
 *
 * 위반 항목마다 별도 행으로 저장한다.
 * /evolve 에서 rule_id + category 로 GROUP BY 해서 패턴을 분석한다.
 *
 * 트랜잭션 경계:
 * - 메서드 전체를 하나의 트랜잭션으로 묶어 violations 가 "전부 저장 or 전부 롤백" 되게 한다.
 * - 엔티티 생성 실패는 미리 걸러내고, DB 실패는 트랜잭션 롤백 + 경고 로그 (본 흐름 보호).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardrailFailurePersistenceAdapter implements GuardrailFailurePort {

    private final GuardrailFailureRepository repository;

    @Override
    @Transactional
    public void save(
            List<GuardrailViolation> violations,
            String category,
            String keyword,
            Long aiMidPrice,
            Long searchMedian,
            int attemptCount
    ) {
        if (violations == null || violations.isEmpty()) {
            return;
        }

        List<GuardrailFailureEntity> entities = new ArrayList<>(violations.size());
        for (GuardrailViolation violation : violations) {
            entities.add(GuardrailFailureEntity.builder()
                    .ruleId(violation.ruleId())
                    .severity(violation.severity().name())
                    .category(category)
                    .keyword(keyword)
                    .violationMessage(violation.message())
                    .aiMidPrice(aiMidPrice)
                    .searchMedianPrice(searchMedian)
                    .attemptCount(attemptCount)
                    .build());
        }

        try {
            repository.saveAll(entities);
            log.info("가드레일 실패 기록 저장 - count={}, category={}, keyword={}",
                    entities.size(), category, keyword);
        } catch (RuntimeException e) {
            // 트랜잭션 롤백 + 상위 흐름 보호 (AiAssistService.recordFailure 가 catch)
            log.warn("가드레일 실패 기록 저장 실패 - count={}, error={}",
                    entities.size(), e.getMessage());
            throw e;
        }
    }
}
