package com.cos.fairbid.ai.adapter.out.naver.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 네이버 쇼핑 검색 API 응답 페이로드.
 *
 * 엔드포인트: GET https://openapi.naver.com/v1/search/shop.json
 * 문서: https://developers.naver.com/docs/serviceapi/search/shopping/shopping.md
 *
 * 사용하지 않는 필드(lastBuildDate, start 등)는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverShoppingResponse(
        Integer total,
        Integer display,
        List<Item> items
) {

    /**
     * 단일 검색 항목.
     *
     * - title 에는 검색어와 일치하는 부분이 {@code <b>...</b>} 로 감싸져 있다 → 어댑터에서 제거.
     * - lprice/hprice 는 문자열로 응답되므로 long 으로 파싱한다.
     * - hprice 는 가격비교가 아닌 단일 상품의 경우 "0" 으로 응답될 수 있다 → null 처리.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String link,
            String image,
            String lprice,
            String hprice,
            String mallName,
            String productId,
            String productType,
            String brand,
            String maker,
            String category1,
            String category2,
            String category3,
            String category4
    ) {
    }
}
