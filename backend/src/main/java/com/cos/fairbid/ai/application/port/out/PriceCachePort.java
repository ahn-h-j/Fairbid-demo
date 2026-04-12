package com.cos.fairbid.ai.application.port.out;

import java.util.Optional;

import com.cos.fairbid.ai.domain.AiAssistResult;

/**
 * AI 시세 캐시 포트 (Phase 2).
 *
 * 자주 조회되는 상품은 Redis 에 적재해 2차 Claude 호출 자체를 우회한다.
 * 키: (category, productKey, grade). 값: 추천가 + 설명 + confidence.
 * TTL: 7일.
 */
public interface PriceCachePort {

    /**
     * 캐시된 시세 조회.
     *
     * @return 존재하면 저장된 결과, 없으면 empty
     */
    Optional<AiAssistResult> find(String category, String productKey, String grade);

    /**
     * 시세 적재. 기존 키가 있으면 덮어쓴다 (최신 시세 반영).
     */
    void save(String category, String productKey, String grade, AiAssistResult result);
}
