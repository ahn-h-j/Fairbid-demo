package com.cos.fairbid.ai.domain.guardrail;

import java.util.List;

/**
 * 출력 가드레일 검증 결과.
 *
 * - HARD 위반이 하나라도 있으면 재시도 대상.
 * - SOFT 위반만 있으면 결과 반환 + DB 기록.
 * - 위반 없으면 통과.
 *
 * @param violations 위반 항목 리스트 (비어있으면 통과)
 */
public record OutputValidation(
        List<GuardrailViolation> violations
) {
    public static OutputValidation pass() {
        return new OutputValidation(List.of());
    }

    public boolean passed() {
        return violations == null || violations.isEmpty();
    }

    public boolean hasHardViolation() {
        return violations != null && violations.stream().anyMatch(GuardrailViolation::isHard);
    }

    /** HARD 위반만 필터링 — 재시도 시 피드백으로 사용 */
    public List<GuardrailViolation> hardViolations() {
        if (violations == null) {
            return List.of();
        }
        return violations.stream().filter(GuardrailViolation::isHard).toList();
    }
}
