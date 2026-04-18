package com.cos.fairbid.ai.adapter.out.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI Chat Completions API 요청 페이로드.
 *
 * 실제 호출:
 * POST {base-url}/v1/chat/completions
 * Headers: Authorization: Bearer {apiKey}
 * Body: OpenAiChatRequest
 *
 * - system 은 messages[0] 에 role=system 으로 포함한다 (Claude 와 달리 별도 필드 없음).
 * - response_format: json_object 로 강제 JSON 출력 (프롬프트에 "JSON" 키워드 필수).
 * - OpenAI 는 프롬프트 캐싱이 자동 — 별도 cache_control 필드 없음.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        // GPT-5 계열부터 max_tokens 가 deprecated → max_completion_tokens 로 통일.
        // gpt-4.1 계열에서도 max_completion_tokens 를 동일하게 수용한다.
        @JsonProperty("max_completion_tokens") Integer maxTokens,
        @JsonProperty("response_format") ResponseFormat responseFormat
) {

    /**
     * Chat 메시지. role=system/user/assistant.
     *
     * content 타입:
     * - system: String (단순 텍스트)
     * - user: String 또는 List<ContentPart> (멀티모달)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            String role,
            Object content
    ) {
        public static Message system(String text) {
            return new Message("system", text);
        }

        public static Message user(List<ContentPart> parts) {
            return new Message("user", parts);
        }

        public static Message userText(String text) {
            return new Message("user", text);
        }
    }

    /**
     * 멀티모달 content 파트.
     * type=text 면 text 필드, type=image_url 이면 imageUrl 필드만 채워진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentPart(
            String type,
            String text,
            @JsonProperty("image_url") ImageUrl imageUrl
    ) {
        public static ContentPart text(String text) {
            return new ContentPart("text", text, null);
        }

        public static ContentPart imageUrl(String url) {
            return new ContentPart("image_url", null, new ImageUrl(url));
        }
    }

    public record ImageUrl(String url) {
    }

    /**
     * 응답 형식 지정.
     * type=json_object 면 모델이 유효한 JSON 을 출력하도록 강제된다.
     */
    public record ResponseFormat(String type) {
        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }
}
