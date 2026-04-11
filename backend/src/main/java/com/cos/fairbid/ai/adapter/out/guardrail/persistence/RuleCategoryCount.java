package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

/**
 * JPQL 프로젝션용 — rule_id + category 조합별 위반 건수.
 */
public record RuleCategoryCount(String ruleId, String category, long count) {
}
