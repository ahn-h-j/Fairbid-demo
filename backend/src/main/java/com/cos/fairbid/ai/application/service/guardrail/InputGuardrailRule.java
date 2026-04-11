package com.cos.fairbid.ai.application.service.guardrail;

import java.util.List;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;

/**
 * 입력 가드레일 규칙 인터페이스.
 *
 * Claude 호출 전에 검색 결과를 검사한다.
 * 반환되는 문자열이 Claude 프롬프트의 주의사항으로 주입된다.
 * 빈 리스트를 반환하면 이 규칙은 통과.
 */
public interface InputGuardrailRule {

    String ruleId();

    /**
     * 검색 결과를 검사해 경고 문자열을 반환한다.
     *
     * @param command    AI 호출 커맨드 (memo, category 등)
     * @param priceItems 시세 검색 결과
     * @return 경고 문자열 리스트 (비어있으면 통과)
     */
    List<String> check(AiAssistCommand command, List<PriceItem> priceItems);
}
