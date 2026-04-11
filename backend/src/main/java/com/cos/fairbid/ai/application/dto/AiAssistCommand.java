package com.cos.fairbid.ai.application.dto;

import java.util.List;

import com.cos.fairbid.auction.domain.Category;

/**
 * AI 경매 어시스턴트 호출 Command.
 *
 * - category 는 nullable: 사용자가 카테고리를 선택하지 않은 상태에서도 AI 추천을 받을 수 있다.
 *   null 인 경우 AI 가 이미지와 memo 로 카테고리를 추론한다.
 * - title 은 별도로 받지 않는다. 상품 식별 정보는 memo 의 "상품 정보" 라인에서 추출한다.
 * - memo 는 프론트엔드에서 구조화된 힌트(상품 정보 / 구매 시기 / 상태 / 추가 정보) 를 자연어로 조립해 보낸다.
 * - priceItems 는 v2 Phase 1 에서 추가: PriceSearchPort 가 조회한 시세 raw 데이터.
 *   null 이면 시세 hint 없이 Claude 가 단독 추론한다 (v1 fallback 또는 검색 실패).
 */
public record AiAssistCommand(
        Category category,
        String memo,
        List<String> imageUrls,
        List<PriceItem> priceItems
) {
    /**
     * v1 호환 생성자 — priceItems 없이 호출.
     */
    public AiAssistCommand(Category category, String memo, List<String> imageUrls) {
        this(category, memo, imageUrls, null);
    }
}
