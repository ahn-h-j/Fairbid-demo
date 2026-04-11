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
 * 출력 가드레일 (SOFT): 페르소나 부재 감지.
 *
 * 시스템 프롬프트 "마케터 체크리스트" 중 "페르소나가 명확한가?" 항목을 강제한다.
 * 설명문에 구매자 페르소나를 지칭하는 표현이 없으면 SOFT 위반.
 *
 * 예시 페르소나 표현:
 * - "~하시는 분", "~하려는 분"
 * - "~이 필요하신 분", "~이 부담스러웠던 분"
 * - "~을 시작하려는 분", "~을 찾으시던 분"
 */
@Component
public class PersonaRule implements OutputGuardrailRule {

    private static final String RULE_ID = "DESCRIPTION_NO_PERSONA";

    /** 페르소나 마커 — 구매자를 지칭하는 한국어 표현 패턴 */
    private static final List<String> PERSONA_MARKERS = List.of(
            "하시는 분", "하시던 분", "하려는 분", "하려던 분",
            "필요하신 분", "필요하셨던 분",
            "찾으시던 분", "찾고 계신 분", "찾던 분",
            "부담스러웠던 분", "부담스러우신 분",
            "망설이셨던 분", "망설이던 분",
            "시작하려는 분", "시작하시려는 분",
            "원하시는 분", "원하셨던 분",
            "관심 있으신 분", "관심 있는 분",
            "써보고 싶으신 분", "써보려는 분",
            "가지고 싶으신 분"
    );

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<GuardrailViolation> check(AiAssistResult result, AiAssistCommand command, List<PriceItem> priceItems) {
        String description = result.generatedDescription();
        if (description == null || description.isBlank()) {
            return List.of();
        }

        boolean hasPersona = PERSONA_MARKERS.stream().anyMatch(description::contains);
        if (hasPersona) {
            return List.of();
        }

        return List.of(new GuardrailViolation(
                RULE_ID, GuardrailSeverity.SOFT,
                "페르소나 표현 부재 — '~하시는 분' 같은 구매자 타겟 표현이 없음"
        ));
    }
}
