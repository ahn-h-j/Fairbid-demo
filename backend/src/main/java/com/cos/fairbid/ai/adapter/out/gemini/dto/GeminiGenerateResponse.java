package com.cos.fairbid.ai.adapter.out.gemini.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gemini generateContent API 응답 페이로드.
 * 사용하지 않는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiGenerateResponse(
        List<Candidate> candidates,
        @JsonProperty("usageMetadata") UsageMetadata usageMetadata,
        @JsonProperty("modelVersion") String modelVersion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
            Content content,
            @JsonProperty("finishReason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String role,
            List<Part> parts
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
            String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageMetadata(
            @JsonProperty("promptTokenCount") Integer promptTokenCount,
            @JsonProperty("candidatesTokenCount") Integer candidatesTokenCount,
            @JsonProperty("totalTokenCount") Integer totalTokenCount,
            @JsonProperty("cachedContentTokenCount") Integer cachedContentTokenCount
    ) {
    }
}
