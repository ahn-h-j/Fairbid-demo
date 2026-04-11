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
 * 출력 가드레일 (SOFT): 단순 리포맷팅 감지.
 *
 * 시스템 프롬프트: "단순 리포맷팅이 아닌가?" — 사용자 memo 를 그대로 옮겨 쓴 것 금지.
 *
 * 판정: memo 의 각 라인 중 20자 이상인 라인이 설명에 그대로 (또는 80% 일치) 등장하면 위반.
 */
@Component
public class ReformatRule implements OutputGuardrailRule {

    private static final String RULE_ID = "DESCRIPTION_REFORMAT";

    /** 검사 대상 memo 라인의 최소 길이 (너무 짧으면 우연 일치 가능성) */
    private static final int MIN_LINE_LENGTH = 20;

    /** 이 수 이상의 라인이 그대로 복사되면 위반 */
    private static final int COPY_THRESHOLD = 2;

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
        if (command.memo() == null || command.memo().isBlank()) {
            return List.of();
        }

        int copiedCount = 0;
        List<String> copiedLines = new java.util.ArrayList<>();

        for (String memoLine : command.memo().split("\\n")) {
            String stripped = stripLabel(memoLine).trim();
            if (stripped.length() < MIN_LINE_LENGTH) {
                continue;
            }
            if (description.contains(stripped)) {
                copiedCount++;
                copiedLines.add(stripped);
                if (copiedCount >= COPY_THRESHOLD) {
                    break;
                }
            }
        }

        if (copiedCount >= COPY_THRESHOLD) {
            return List.of(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.SOFT,
                    "단순 리포맷팅 의심 — memo 라인 " + copiedCount + "개가 설명에 그대로 복사됨: " + copiedLines
            ));
        }

        return List.of();
    }

    /** memo 라인에서 "상품 정보:", "추가 정보:" 같은 라벨 제거 */
    private String stripLabel(String line) {
        int colonIdx = line.indexOf(':');
        if (colonIdx > 0 && colonIdx < 15) {
            return line.substring(colonIdx + 1);
        }
        return line;
    }
}
