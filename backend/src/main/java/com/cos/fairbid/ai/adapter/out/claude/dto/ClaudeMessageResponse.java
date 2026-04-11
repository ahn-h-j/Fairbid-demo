package com.cos.fairbid.ai.adapter.out.claude.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic Messages API 응답 페이로드.
 * 사용하지 않는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeMessageResponse(
        String id,
        String type,
        String role,
        List<ContentBlock> content,
        String model,
        @JsonProperty("stop_reason") String stopReason,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            String type,
            String text
    ) {
    }

    /**
     * 토큰 사용량.
     *
     * - input/output: 매 호출 발생
     * - cache_creation/cache_read: 프롬프트 캐싱 사용 시에만 채워짐 (v1 은 미사용이라 보통 null)
     * - server_tool_use: web_search 같은 서버 사이드 도구 사용량
     *
     * 미사용 호출에서 필드가 누락되어도 record 가 null 로 받도록 모두 wrapper 타입으로 둔다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("cache_creation_input_tokens") Integer cacheCreationInputTokens,
            @JsonProperty("cache_read_input_tokens") Integer cacheReadInputTokens,
            @JsonProperty("server_tool_use") ServerToolUse serverToolUse
    ) {
    }

    /**
     * 서버 사이드 도구 (web_search 등) 사용량.
     * 도구가 호출되지 않은 응답에서는 usage.server_tool_use 자체가 없다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServerToolUse(
            @JsonProperty("web_search_requests") Integer webSearchRequests
    ) {
    }
}
