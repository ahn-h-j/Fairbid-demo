package com.cos.fairbid.ai.adapter.out.naver.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 네이버 카페 검색 API 응답 페이로드.
 *
 * 엔드포인트: GET https://openapi.naver.com/v1/search/cafearticle.json
 * 문서: https://developers.naver.com/docs/serviceapi/search/cafearticle/cafearticle.md
 *
 * 중고나라/번개장터 등 중고거래 카페글에서 실 거래가를 수집하는 용도.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverCafeResponse(
        Integer total,
        Integer display,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String link,
            String description,
            String cafename,
            String cafeurl
    ) {
    }
}
