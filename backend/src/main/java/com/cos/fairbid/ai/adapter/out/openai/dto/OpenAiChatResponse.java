package com.cos.fairbid.ai.adapter.out.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI Chat Completions API 응답 페이로드.
 * 사용하지 않는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content
    ) {
    }

    /**
     * 토큰 사용량.
     *
     * - prompt_tokens / completion_tokens: 매 호출 발생
     * - prompt_tokens_details.cached_tokens: 자동 프롬프트 캐시에서 재사용된 토큰 (2024-10~ 지원)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptTokensDetails(
            @JsonProperty("cached_tokens") Integer cachedTokens
    ) {
    }
}
