package com.cos.fairbid.ai.application.dto;

import com.cos.fairbid.auction.domain.Category;

import java.util.List;

/**
 * AI 경매 어시스턴트 호출 Command.
 *
 * - category 는 nullable: 사용자가 카테고리를 선택하지 않은 상태에서도 AI 추천을 받을 수 있다.
 *   null 인 경우 AI 가 이미지와 memo 로 카테고리를 추론한다.
 * - title 은 별도로 받지 않는다. 상품 식별 정보는 memo 의 "상품 정보" 라인에서 추출한다.
 * - memo 는 프론트엔드에서 구조화된 힌트(상품 정보 / 구매 시기 / 상태 / 추가 정보) 를 자연어로 조립해 보낸다.
 */
public record AiAssistCommand(
        Category category,
        String memo,
        List<String> imageUrls
) {
}
