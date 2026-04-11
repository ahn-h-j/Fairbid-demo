package com.cos.fairbid.ai.domain.guardrail;

/**
 * 가드레일 위반 항목 값 객체.
 *
 * @param ruleId   위반 규칙 식별자 (예: "PRICE_INVERSION", "SEARCH_MISMATCH")
 * @param severity HARD(재시도) / SOFT(기록만)
 * @param message  사람 읽기용 위반 상세 (예: "low(500,000) >= mid(450,000)")
 */
public record GuardrailViolation(
        String ruleId,
        GuardrailSeverity severity,
        String message
) {
    public boolean isHard() {
        return severity == GuardrailSeverity.HARD;
    }
}
