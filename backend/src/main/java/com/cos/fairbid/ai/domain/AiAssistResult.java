package com.cos.fairbid.ai.domain;

/**
 * AI 경매 어시스턴트 결과 도메인 객체.
 * Claude가 생성한 시작가 추천(3구간)과 상품 설명(Markdown)을 담는다.
 */
public record AiAssistResult(
        SuggestedPrices suggestedPrices,
        String generatedDescription
) {
}
