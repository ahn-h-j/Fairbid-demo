package com.cos.fairbid.ai.domain.guardrail;

/**
 * 가드레일 위반 심각도.
 *
 * - HARD: 구조적 오류 (가격 역전, 파싱 실패 등). 재시도 대상.
 * - SOFT: 판단 영역 (가격 괴리 등). DB 기록만 하고 결과는 그대로 반환.
 */
public enum GuardrailSeverity {
    HARD,
    SOFT
}
