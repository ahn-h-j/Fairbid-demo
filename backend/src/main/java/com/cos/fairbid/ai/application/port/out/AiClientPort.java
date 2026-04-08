package com.cos.fairbid.ai.application.port.out;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.domain.AiAssistResult;

/**
 * AI 모델 클라이언트 Port Out.
 * v1 구현체: ClaudeApiAdapter (Anthropic Messages API + Vision + Prompt Caching)
 *
 * v2에서 시세 테이블 캐시가 들어가면 이 포트 위에 데코레이터로 캐시 어댑터가 추가될 수 있다.
 */
public interface AiClientPort {

    AiAssistResult generate(AiAssistCommand command);
}
