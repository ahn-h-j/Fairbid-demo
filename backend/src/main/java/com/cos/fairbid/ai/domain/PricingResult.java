package com.cos.fairbid.ai.domain;

/**
 * AI 가격 추천 phase2a 결과. 설명을 제외한 가격 + confidence 정보만 담는다.
 *
 * <p>SPEC §19 옵션 B: Claude 는 가격 전용으로 축소하고 설명은 {@code DescriptionGeneratorPort}
 * (Gemini) 로 분리한다. 서비스 레이어에서 {@link AiAssistResult} 로 조립한다.</p>
 *
 * @param suggestedPrices  추천가 3구간
 * @param confidence       "high" / "low"
 * @param confidenceReason low 일 때 이유
 */
public record PricingResult(
        SuggestedPrices suggestedPrices,
        String confidence,
        String confidenceReason
) {
    public boolean isLowConfidence() {
        return "low".equalsIgnoreCase(confidence);
    }
}
