package com.cos.fairbid.ai.adapter.out.guardrail.rules;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailRule;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailSeverity;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 출력 가드레일 (SOFT): 광고 문구 품질 검증.
 *
 * 시스템 프롬프트의 "절대 금지" 클리셰/엔지니어 톤 위반 탐지.
 * SOFT 위반 — 재시도는 하지 않고 DB 에 기록만 한다.
 * /evolve 가 이 데이터로 패턴 분석 → 프롬프트 클리셰 목록 보강.
 */
@Component
public class DescriptionQualityRule implements OutputGuardrailRule {

    private static final String RULE_ID = "DESCRIPTION_QUALITY";

    /** 클리셰 / 마케팅 상투어 — 시스템 프롬프트 "절대 금지" 섹션과 일치 */
    private static final List<String> BANNED_PHRASES = List.of(
            "합리적인 가격",
            "최저가",
            "특가",
            "한정 수량",
            "100% 정품 보장",
            "100프로 정품",
            "후회 없는 선택",
            "강력 추천",
            "이번 기회",
            "편리하게 사용 가능",
            "다양하게 활용",
            "그대로 전달드립니다",
            "절대 후회",
            "확실히 만족"
    );

    /** 엔지니어 톤 의심 키워드 (스펙 나열 패턴) */
    private static final List<String> SPEC_KEYWORDS = List.of(
            "칩셋을 탑재", "디스플레이를 탑재", "프로세서를 탑재",
            "탑재한 14인치", "탑재한 15인치", "탑재한 16인치",
            "Liquid Retina", "Apple Silicon", "ProMotion"
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

        List<GuardrailViolation> violations = new ArrayList<>();

        // 1. 클리셰 탐지
        List<String> foundCliches = BANNED_PHRASES.stream()
                .filter(description::contains)
                .toList();
        if (!foundCliches.isEmpty()) {
            violations.add(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.SOFT,
                    "클리셰 포함: " + String.join(", ", foundCliches)
            ));
        }

        // 2. 엔지니어 톤 탐지
        List<String> foundSpecs = SPEC_KEYWORDS.stream()
                .filter(description::contains)
                .toList();
        if (!foundSpecs.isEmpty()) {
            violations.add(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.SOFT,
                    "엔지니어 톤(스펙 나열): " + String.join(", ", foundSpecs)
            ));
        }

        // 3. 사양 나열 패턴 — 불릿 3줄 이상 연속
        int bulletCount = 0;
        for (String line : description.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                bulletCount++;
                if (bulletCount >= 3) {
                    violations.add(new GuardrailViolation(
                            RULE_ID, GuardrailSeverity.SOFT,
                            "사양 나열 의심: 불릿 3줄 이상 연속"
                    ));
                    break;
                }
            } else if (!trimmed.isEmpty()) {
                bulletCount = 0;
            }
        }

        return violations;
    }
}
