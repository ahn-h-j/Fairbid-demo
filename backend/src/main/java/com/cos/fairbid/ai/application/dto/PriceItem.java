package com.cos.fairbid.ai.application.dto;

/**
 * 시세 검색 결과 단일 항목.
 *
 * v2 Phase 1 — {@code PriceSearchPort.search()} 의 반환 요소.
 * 외부 검색 API (네이버 쇼핑/카페) 응답을 정규화한 형태이며, ClaudePromptBuilder 가
 * 이 항목들을 user message 의 시세 hint 블록으로 포매팅한다.
 *
 * 두 가지 출처:
 * - 쇼핑: lprice/hprice 가 있고 description 은 null. 신품 가격.
 * - 카페: lprice=0, description 이 있음. 중고 거래글 본문 발췌.
 *
 * Light 가공 원칙: 평균/추천가 같은 derived 값을 만들지 않는다. raw 를 그대로 들고 있고
 * 매칭/판단은 Claude 에게 맡긴다.
 */
public record PriceItem(
        String title,
        long lprice,
        Long hprice,
        String mallName,
        String brand,
        String category,
        /** 카페글 본문 발췌 (쇼핑 항목이면 null). Claude 가 여기서 실 거래가를 파악한다. */
        String description
) {
    /** 쇼핑 항목용 생성자 (description 없음) */
    public PriceItem(String title, long lprice, Long hprice, String mallName, String brand, String category) {
        this(title, lprice, hprice, mallName, brand, category, null);
    }
}
