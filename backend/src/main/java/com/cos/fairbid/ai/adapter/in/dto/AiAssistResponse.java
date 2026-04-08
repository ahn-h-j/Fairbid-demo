package com.cos.fairbid.ai.adapter.in.dto;

import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;

/**
 * AI 경매 어시스턴트 응답 DTO.
 *
 * 응답 형식 (ApiResponse 래퍼 안에 들어감):
 * {
 *   "suggestedPrices": { "low": 450000, "mid": 520000, "high": 600000 },
 *   "generatedDescription": "## 아이패드 프로 ..."
 * }
 */
public record AiAssistResponse(
        PriceRange suggestedPrices,
        String generatedDescription
) {

    public static AiAssistResponse from(AiAssistResult result) {
        return new AiAssistResponse(
                PriceRange.from(result.suggestedPrices()),
                result.generatedDescription()
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
