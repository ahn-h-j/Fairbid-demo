package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가드레일 실패 기록 Entity.
 *
 * 출력 가드레일에서 위반이 발생하면 이 테이블에 기록된다.
 * /evolve 가 이 데이터를 읽어 패턴 분석 후 규칙을 진화시킨다.
 */
@Entity
@Table(name = "guardrail_failure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardrailFailureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기록 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 위반 규칙 ID (예: PRICE_STRUCTURE, SEARCH_MISMATCH) */
    @Column(nullable = false, length = 64)
    private String ruleId;

    /** 위반 심각도 (HARD / SOFT) */
    @Column(nullable = false, length = 16)
    private String severity;

    /** 상품 카테고리 */
    @Column(length = 32)
    private String category;

    /** 검색 키워드 */
    @Column(length = 256)
    private String keyword;

    /** 위반 상세 메시지 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String violationMessage;

    /** Claude 추천 mid 가격 */
    private Long aiMidPrice;

    /** 검색 결과 중앙값 */
    private Long searchMedianPrice;

    /** 시도 횟수 (1 = 첫 시도, 2 = 재시도 후) */
    @Column(nullable = false)
    private Integer attemptCount;

    /** /evolve 에서 처리됨 여부 */
    @Column(nullable = false)
    private Boolean resolved;

    @Builder
    public GuardrailFailureEntity(
            String ruleId,
            String severity,
            String category,
            String keyword,
            String violationMessage,
            Long aiMidPrice,
            Long searchMedianPrice,
            Integer attemptCount
    ) {
        this.createdAt = LocalDateTime.now();
        this.ruleId = ruleId;
        this.severity = severity;
        this.category = category;
        this.keyword = keyword;
        this.violationMessage = violationMessage;
        this.aiMidPrice = aiMidPrice;
        this.searchMedianPrice = searchMedianPrice;
        this.attemptCount = attemptCount;
        this.resolved = false;
    }
}
