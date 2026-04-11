package com.cos.fairbid.ai.adapter.out.guardrail.rules;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailRule;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailSeverity;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 출력 가드레일 (SOFT): confidence=low 케이스 추적.
 *
 * Claude 가 스스로 "검색 결과가 부실해 학습 지식 기반 추정"이라고 판단한 케이스를
 * 기록해서 /evolve 가 패턴 분석할 수 있게 한다.
 *
 * 이 규칙은 "틀렸다"가 아니라 "검색 품질 약한 케이스 누적 집계" 용도.
 * 카테고리별로 얼마나 자주 발생하는지 보면 어느 카테고리 검색 키워드/파이프라인을
 * 개선해야 할지 판단할 수 있다.
 */
@Component
public class ConfidenceTrackingRule implements OutputGuardrailRule {

    private static final String RULE_ID = "CONFIDENCE_LOW";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<GuardrailViolation> check(AiAssistResult result, AiAssistCommand command, List<PriceItem> priceItems) {
        if (!result.isLowConfidence()) {
            return List.of();
        }

        String reason = result.confidenceReason() != null ? result.confidenceReason() : "사유 없음";
        return List.of(new GuardrailViolation(
                RULE_ID, GuardrailSeverity.SOFT,
                "low confidence — " + reason
        ));
    }
}
