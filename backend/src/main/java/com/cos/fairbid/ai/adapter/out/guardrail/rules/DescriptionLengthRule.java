package com.cos.fairbid.ai.adapter.out.guardrail.rules;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailRule;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailSeverity;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 출력 가드레일 (HARD): 상품 설명 길이 검증.
 *
 * 시스템 프롬프트가 180~450자를 요구한다.
 * 이 범위를 벗어나면 재시도 대상.
 */
@Component
public class DescriptionLengthRule implements OutputGuardrailRule {

    private static final String RULE_ID = "DESCRIPTION_LENGTH";
    private static final int MIN_LENGTH = 180;
    private static final int MAX_LENGTH = 450;

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<GuardrailViolation> check(AiAssistResult result, AiAssistCommand command, List<PriceItem> priceItems) {
        String description = result.generatedDescription();

        if (description == null || description.isBlank()) {
            return List.of(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    "상품 설명이 비어있습니다. 180~450자 범위의 설명을 생성해주세요."
            ));
        }

        int length = description.length();

        if (length < MIN_LENGTH) {
            return List.of(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    String.format("상품 설명이 %d자입니다 — 최소 %d자 이상으로 작성해주세요.", length, MIN_LENGTH)
            ));
        }

        if (length > MAX_LENGTH) {
            return List.of(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    String.format("상품 설명이 %d자입니다 — 최대 %d자 이하로 작성해주세요.", length, MAX_LENGTH)
            ));
        }

        return Collections.emptyList();
    }
}
