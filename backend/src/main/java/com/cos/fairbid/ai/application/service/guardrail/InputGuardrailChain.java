package com.cos.fairbid.ai.application.service.guardrail;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.domain.exception.PromptInjectionDetectedException;

/**
 * 입력 가드레일 체인.
 *
 * Spring 이 같은 인터페이스의 구현체들을 {@code List<InputGuardrailRule>} 로 자동 수집한다.
 * 새 규칙을 추가하려면 {@link InputGuardrailRule} 구현체를 {@code @Component} 로 등록하면 끝.
 *
 * 규칙 발동 시 처리:
 * - PROMPT_INJECTION: 즉시 {@link PromptInjectionDetectedException} throw → 요청 차단
 * - 그 외: 로그만 남기고 통과 (현재 다른 HARD 입력 규칙 없음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InputGuardrailChain {

    private static final String RULE_PROMPT_INJECTION = "PROMPT_INJECTION";

    private final List<InputGuardrailRule> rules;

    /**
     * 등록된 모든 입력 규칙을 순회한다.
     * PROMPT_INJECTION 규칙이 발동하면 즉시 예외를 throw 한다.
     *
     * @param command AI 호출 커맨드
     * @throws PromptInjectionDetectedException 프롬프트 인젝션 탐지 시
     */
    public void validate(AiAssistCommand command) {
        for (InputGuardrailRule rule : rules) {
            try {
                List<String> violations = rule.check(command, Collections.emptyList());
                if (violations != null && !violations.isEmpty()) {
                    if (RULE_PROMPT_INJECTION.equals(rule.ruleId())) {
                        // 보안 규칙 — 즉시 차단
                        log.warn("프롬프트 인젝션 차단 - rule={}, detail={}",
                                rule.ruleId(), violations);
                        throw PromptInjectionDetectedException.of();
                    }
                    log.info("입력 가드레일 경고 - rule={}, detail={}", rule.ruleId(), violations);
                }
            } catch (PromptInjectionDetectedException e) {
                throw e;
            } catch (Exception e) {
                // 가드레일 규칙 자체의 실패는 무시 — 본 흐름을 막지 않는다
                log.warn("입력 가드레일 규칙 실행 실패 - rule={}, error={}", rule.ruleId(), e.getMessage());
            }
        }
    }
}
