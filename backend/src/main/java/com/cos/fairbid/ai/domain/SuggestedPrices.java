package com.cos.fairbid.ai.domain;

/**
 * AI가 추천한 시작가 3구간 (보수적 / 적정 / 공격적).
 * 순수 값 객체 — 검증 로직은 v3 하네스 도입 시 추가.
 */
public record SuggestedPrices(
        Long low,
        Long mid,
        Long high
) {
}
