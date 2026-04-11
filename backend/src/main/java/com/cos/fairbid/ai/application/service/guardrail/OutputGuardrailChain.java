package com.cos.fairbid.ai.application.service.guardrail;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;
import com.cos.fairbid.ai.domain.guardrail.OutputValidation;

/**
 * 출력 가드레일 체인.
 *
 * Spring 이 같은 인터페이스의 구현체들을 {@code List<OutputGuardrailRule>} 로 자동 수집한다.
 * 새 규칙을 추가하려면 {@link OutputGuardrailRule} 구현체를 {@code @Component} 로 등록하면 끝.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutputGuardrailChain {

    private final List<OutputGuardrailRule> rules;

    /**
     * 등록된 모든 출력 규칙을 순회하며 위반 항목을 모은다.
     *
     * @param result     Claude 응답 결과
     * @param command    원본 커맨드
     * @param priceItems 시세 검색 결과 (교차 검증용)
     * @return 출력 검증 결과 (violations 리스트)
     */
    public OutputValidation validate(AiAssistResult result, AiAssistCommand command, List<PriceItem> priceItems) {
        List<GuardrailViolation> violations = new ArrayList<>();

        for (OutputGuardrailRule rule : rules) {
            try {
                List<GuardrailViolation> ruleViolations = rule.check(result, command, priceItems);
                if (ruleViolations != null && !ruleViolations.isEmpty()) {
                    log.info("출력 가드레일 위반 - rule={}, violations={}",
                            rule.ruleId(), ruleViolations.size());
                    violations.addAll(ruleViolations);
                }
            } catch (Exception e) {
                // 가드레일 규칙 자체의 실패는 무시 — 본 흐름을 막지 않는다
                log.warn("출력 가드레일 규칙 실행 실패 - rule={}, error={}", rule.ruleId(), e.getMessage());
            }
        }

        return new OutputValidation(violations);
    }
}
