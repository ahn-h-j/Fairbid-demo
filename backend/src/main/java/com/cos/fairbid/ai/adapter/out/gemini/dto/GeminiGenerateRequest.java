package com.cos.fairbid.ai.adapter.out.gemini.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gemini generateContent API 요청 페이로드.
 *
 * 실제 호출:
 * POST {base}/v1beta/models/{model}:generateContent?key={api_key}
 *
 * - system_instruction: Claude/OpenAI 의 system 프롬프트에 대응
 * - contents: 실제 사용자 메시지 (role=user)
 * - 이미지는 inline_data (base64 encoded) 로 전달 — 임의 HTTP URL 은 file_uri 로 받지 않음
 * - generationConfig.responseMimeType=application/json 으로 JSON 출력 강제
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiGenerateRequest(
        @JsonProperty("system_instruction") Content systemInstruction,
        List<Content> contents,
        @JsonProperty("generationConfig") GenerationConfig generationConfig
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(
            String role,
            List<Part> parts
    ) {
        public static Content system(String text) {
            return new Content(null, List.of(Part.text(text)));
        }

        public static Content user(List<Part> parts) {
            return new Content("user", parts);
        }
    }

    /**
     * 메시지 파트. text / inline_data 중 하나만 채워진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(
            String text,
            @JsonProperty("inline_data") InlineData inlineData
    ) {
        public static Part text(String text) {
            return new Part(text, null);
        }

        public static Part image(String mimeType, String base64Data) {
            return new Part(null, new InlineData(mimeType, base64Data));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InlineData(
            @JsonProperty("mime_type") String mimeType,
            String data
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(
            @JsonProperty("maxOutputTokens") Integer maxOutputTokens,
            @JsonProperty("responseMimeType") String responseMimeType
    ) {
        public static GenerationConfig jsonOutput(Integer maxOutputTokens) {
            return new GenerationConfig(maxOutputTokens, "application/json");
        }
    }
}
