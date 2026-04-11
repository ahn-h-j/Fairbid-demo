package com.cos.fairbid.ai.adapter.out.guardrail.persistence;

/**
 * JPQL 프로젝션용 — rule_id 별 위반 건수.
 */
public record RuleCount(String ruleId, long count) {
}
