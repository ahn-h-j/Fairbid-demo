package com.cos.fairbid.ai.application.dto;

/**
 * 1차 Claude 호출 결과: 상품 분석.
 *
 * @param productName   식별된 상품명 (브랜드 + 모델 + 핵심 스펙)
 * @param grade         중고 등급 (S/A/B/C/D)
 * @param gradeReason   등급 판정 근거
 * @param searchKeyword 네이버 검색용 키워드
 * @param productKey    시세 캐시 키 정규화 ID (snake_case, 예: "macbook_pro_14_m3_256gb").
 *                      동일 상품에 대한 요청이 같은 키로 정규화되어야 캐시 hit 이 나므로,
 *                      색상/구매시기/상태 등 개별 요인은 제외하고 모델 식별자만 포함한다.
 */
public record ProductAnalysis(
        String productName,
        String grade,
        String gradeReason,
        String searchKeyword,
        String productKey
) {
}
