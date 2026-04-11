package com.cos.fairbid.ai.adapter.in.dto;

import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;

/**
 * AI 경매 어시스턴트 응답 DTO.
 *
 * - confidence: "high" = 검색 결과 기반 확실, "low" = 학습 지식 기반 추정
 * - confidenceReason: confidence 가 low 일 때 사용자에게 노출할 불확실 사유
 *
 * 프론트는 confidence=low 일 때 "⚠ 참고용 추정치" 배지와 confidenceReason 을 노출한다.
 */
public record AiAssistResponse(
        PriceRange suggestedPrices,
        String generatedDescription,
        String confidence,
        String confidenceReason
) {

    public static AiAssistResponse from(AiAssistResult result) {
        return new AiAssistResponse(
                PriceRange.from(result.suggestedPrices()),
                result.generatedDescription(),
                result.confidence(),
                result.confidenceReason()
        );
    }

    public record PriceRange(
            Long low,
            Long mid,
            Long high
    ) {
        public static PriceRange from(SuggestedPrices prices) {
            return new PriceRange(prices.low(), prices.mid(), prices.high());
        }
    }
}
