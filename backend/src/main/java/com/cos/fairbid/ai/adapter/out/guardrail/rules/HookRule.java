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
 * 출력 가드레일 (SOFT): 첫 H1 헤딩이 단순 상품명(후크 없음)인지 검사.
 *
 * 시스템 프롬프트 마케터 체크리스트: "첫 줄(제목)이 후크인가?"
 *
 * 좋은 예: "## 박스도 안 뜯은 맥북 프로 14 M3" (후크: 박스도 안 뜯은)
 * 나쁜 예: "## 맥북 프로 14 M3" (단순 상품명, 후크 0)
 *
 * 판정 기준: 첫 H1 헤딩이 memo의 "상품 정보" 라인과 80% 이상 일치하면 후크 없음.
 * 또는 헤딩 길이가 25자 미만이면 후크 의심.
 */
@Component
public class HookRule implements OutputGuardrailRule {

    private static final String RULE_ID = "DESCRIPTION_NO_HOOK";

    /** 후크 없는 단순 제목 의심 길이 (25자 미만) */
    private static final int SHORT_HEADING_THRESHOLD = 25;

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

        // 첫 # 헤딩 추출
        String firstHeading = extractFirstHeading(description);
        if (firstHeading == null) {
            return List.of(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.SOFT,
                    "H1 헤딩 부재 — 마크다운 제목이 없음"
            ));
        }

        // 너무 짧은 헤딩은 후크 없음 의심
        if (firstHeading.length() < SHORT_HEADING_THRESHOLD) {
            return List.of(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.SOFT,
                    String.format("첫 헤딩이 너무 짧음 (%d자) — 후크 없는 단순 상품명 의심: \"%s\"",
                            firstHeading.length(), firstHeading)
            ));
        }

        return List.of();
    }

    /**
     * Markdown 본문에서 첫 번째 # / ## / ### 헤딩의 텍스트를 추출한다.
     */
    private String extractFirstHeading(String description) {
        for (String line : description.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                // # 기호와 공백 제거
                return trimmed.replaceAll("^#+\\s*", "").trim();
            }
        }
        return null;
    }
}
