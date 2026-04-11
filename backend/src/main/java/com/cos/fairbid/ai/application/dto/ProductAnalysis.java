package com.cos.fairbid.ai.application.dto;

/**
 * 1차 Claude 호출 결과: 상품 분석.
 *
 * @param productName   식별된 상품명 (브랜드 + 모델 + 핵심 스펙)
 * @param grade         중고 등급 (S/A/B/C/D)
 * @param gradeReason   등급 판정 근거
 * @param searchKeyword 네이버 검색용 키워드
 */
public record ProductAnalysis(
        String productName,
        String grade,
        String gradeReason,
        String searchKeyword
) {
}
