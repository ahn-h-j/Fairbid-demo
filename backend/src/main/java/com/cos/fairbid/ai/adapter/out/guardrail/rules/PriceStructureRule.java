package com.cos.fairbid.ai.adapter.out.guardrail.rules;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailRule;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.guardrail.GuardrailSeverity;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 출력 가드레일 (HARD): 가격 구조 검증.
 *
 * - low < mid < high 순서 위반 (가격 역전)
 * - 0원 또는 음수
 *
 * 구조적 오류이므로 재시도 대상.
 */
@Component
public class PriceStructureRule implements OutputGuardrailRule {

    private static final String RULE_ID = "PRICE_STRUCTURE";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<GuardrailViolation> check(AiAssistResult result, AiAssistCommand command, List<PriceItem> priceItems) {
        List<GuardrailViolation> violations = new ArrayList<>();
        SuggestedPrices prices = result.suggestedPrices();

        // null 체크 — parseResult 에서 이미 걸러지지만 방어
        if (prices.low() == null || prices.mid() == null || prices.high() == null) {
            violations.add(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    "추천가에 null 값이 있습니다."
            ));
            return violations;
        }

        long low = prices.low();
        long mid = prices.mid();
        long high = prices.high();

        // 0원 또는 음수
        if (low <= 0 || mid <= 0 || high <= 0) {
            violations.add(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    String.format("추천가에 0원 이하 값이 있습니다 — low=%,d, mid=%,d, high=%,d. "
                            + "모든 가격은 양수여야 합니다.", low, mid, high)
            ));
        }

        // 가격 역전
        if (low >= mid) {
            violations.add(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    String.format("low(%,d) >= mid(%,d) — low < mid < high 순서로 생성해주세요.", low, mid)
            ));
        }
        if (mid >= high) {
            violations.add(new GuardrailViolation(
                    RULE_ID, GuardrailSeverity.HARD,
                    String.format("mid(%,d) >= high(%,d) — low < mid < high 순서로 생성해주세요.", mid, high)
            ));
        }

        return violations;
    }
}
