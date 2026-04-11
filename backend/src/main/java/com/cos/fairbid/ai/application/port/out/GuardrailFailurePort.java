package com.cos.fairbid.ai.application.port.out;

import java.util.List;

import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 가드레일 실패 기록 저장 포트.
 *
 * 출력 가드레일에서 위반이 발생하면 DB 에 기록한다.
 * /evolve 스킬이 이 데이터를 읽어 패턴을 분석하고 규칙을 진화시킨다.
 */
public interface GuardrailFailurePort {

    /**
     * 가드레일 위반을 기록한다.
     *
     * @param violations    위반 항목 리스트
     * @param category      상품 카테고리 (nullable)
     * @param keyword       검색 키워드 (nullable)
     * @param aiMidPrice    Claude 추천 mid 가격 (nullable)
     * @param searchMedian  검색 결과 중앙값 (nullable)
     * @param attemptCount  시도 횟수 (1 = 첫 시도, 2 = 재시도 후)
     */
    void save(
            List<GuardrailViolation> violations,
            String category,
            String keyword,
            Long aiMidPrice,
            Long searchMedian,
            int attemptCount
    );
}
