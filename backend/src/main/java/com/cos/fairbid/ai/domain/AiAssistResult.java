package com.cos.fairbid.ai.domain;

/**
 * AI 경매 어시스턴트 결과 도메인 객체.
 *
 * @param suggestedPrices      추천가 3구간 (보수적/적정/공격적)
 * @param generatedDescription 마케팅 톤 상품 설명 (Markdown)
 * @param confidence           신뢰도. "high" = 검색 결과 기반 확실, "low" = 학습 지식 기반 추정
 * @param confidenceReason     confidence 가 low 일 때의 불확실한 이유 (프론트에서 사용자에게 노출)
 */
public record AiAssistResult(
        SuggestedPrices suggestedPrices,
        String generatedDescription,
        String confidence,
        String confidenceReason
) {
    /** 기본 high confidence 생성 (하위 호환) */
    public AiAssistResult(SuggestedPrices suggestedPrices, String generatedDescription) {
        this(suggestedPrices, generatedDescription, "high", null);
    }

    public boolean isLowConfidence() {
        return "low".equalsIgnoreCase(confidence);
    }
}
