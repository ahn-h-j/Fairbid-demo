package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 가드레일 실패 기록 JPA Repository.
 */
public interface GuardrailFailureRepository extends JpaRepository<GuardrailFailureEntity, Long> {

    /**
     * 기간 내 rule_id 별 위반 카운트 (내림차순).
     * /evolve 리포트에서 "어떤 규칙이 제일 많이 걸렸는지" 집계용.
     */
    @Query("""
            SELECT new com.cos.fairbid.ai.adapter.out.guardrail.persistence.RuleCount(
                g.ruleId, COUNT(g))
            FROM GuardrailFailureEntity g
            WHERE g.createdAt >= :from AND g.createdAt < :to
            GROUP BY g.ruleId
            ORDER BY COUNT(g) DESC
            """)
    List<RuleCount> countByRuleInPeriod(LocalDateTime from, LocalDateTime to);

    /**
     * 기간 내 rule_id + category 조합별 위반 카운트.
     */
    @Query("""
            SELECT new com.cos.fairbid.ai.adapter.out.guardrail.persistence.RuleCategoryCount(
                g.ruleId, g.category, COUNT(g))
            FROM GuardrailFailureEntity g
            WHERE g.createdAt >= :from AND g.createdAt < :to
            GROUP BY g.ruleId, g.category
            ORDER BY COUNT(g) DESC
            """)
    List<RuleCategoryCount> countByRuleAndCategoryInPeriod(LocalDateTime from, LocalDateTime to);

    /**
     * 기간 내 전체 위반 카운트.
     */
    @Query("SELECT COUNT(g) FROM GuardrailFailureEntity g WHERE g.createdAt >= :from AND g.createdAt < :to")
    long countInPeriod(LocalDateTime from, LocalDateTime to);

    /**
     * 기간 내 특정 rule 의 최근 10건 메시지 (패턴 분석용).
     */
    @Query("""
            SELECT g.violationMessage FROM GuardrailFailureEntity g
            WHERE g.ruleId = :ruleId AND g.createdAt >= :from AND g.createdAt < :to
            ORDER BY g.createdAt DESC
            """)
    List<String> recentMessages(String ruleId, LocalDateTime from, LocalDateTime to,
                                org.springframework.data.domain.Pageable pageable);
}
