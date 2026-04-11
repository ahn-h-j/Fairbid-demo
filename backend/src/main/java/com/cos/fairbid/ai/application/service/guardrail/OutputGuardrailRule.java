package com.cos.fairbid.ai.application.service.guardrail;

import java.util.List;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 출력 가드레일 규칙 인터페이스.
 *
 * Claude 응답을 검사해 위반 항목을 반환한다.
 * HARD 위반은 재시도 대상, SOFT 위반은 DB 기록만.
 */
public interface OutputGuardrailRule {

    String ruleId();

    /**
     * Claude 응답을 검사해 위반 항목을 반환한다.
     *
     * @param result     Claude 응답 결과
     * @param command    원본 커맨드 (검증 컨텍스트 참조용)
     * @param priceItems 시세 검색 결과 (교차 검증용)
     * @return 위반 항목 리스트 (비어있으면 통과)
     */
    List<GuardrailViolation> check(AiAssistResult result, AiAssistCommand command, List<PriceItem> priceItems);
}
