package com.cos.fairbid.ai.application.port.out;

import java.util.List;

import com.cos.fairbid.ai.application.dto.PriceItem;

/**
 * 시세 검색 Port Out.
 *
 * v2 Phase 1 — Anthropic web_search 도구를 우리 검색 파이프라인으로 대체하기 위한 포트.
 * Claude 호출 전에 우리가 직접 외부 검색 API 를 호출해 시세 raw 데이터를 모은다.
 *
 * 구현체 (v2 Phase 1): {@code NaverShoppingAdapter} — 네이버 쇼핑 API
 *
 * 설계 원칙:
 * - Light 가공: 검색 결과를 평균/추천가로 가공하지 않는다. raw 항목 리스트만 반환.
 *   매칭/등급 보정/가격 산정은 Claude 가 시세 데이터를 보고 직접 함.
 * - 결정론적: 네트워크/외부 API 외에는 모델 호출 같은 비결정 요소 없음.
 */
public interface PriceSearchPort {

    /**
     * 키워드로 시세 항목을 조회한다.
     *
     * @param keyword 검색어 (예: "맥북 프로 14 M3 256GB")
     * @param limit   최대 반환 개수
     * @return 시세 항목 리스트. 결과 없으면 빈 리스트 (null 반환 금지).
     *         외부 API 장애 시 빈 리스트 반환 — 호출측에서 시세 hint 없이 진행할 수 있게 한다.
     */
    List<PriceItem> search(String keyword, int limit);
}
