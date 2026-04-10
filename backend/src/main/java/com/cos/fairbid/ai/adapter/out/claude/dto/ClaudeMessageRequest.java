package com.cos.fairbid.ai.adapter.out.claude.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic Messages API 요청 페이로드.
 *
 * 실제 호출:
 * POST {base-url}/v1/messages
 * Body: ClaudeMessageRequest
 *
 * v1:
 * - system 은 단순 문자열로 전달 (프롬프트 캐싱은 v2 에서 도입)
 * - tools 에 web_search 서버 사이드 도구를 활성화해 실시간 시세 조회 가능
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<Message> messages,
        List<Tool> tools
) {

    public record Message(
            String role,
            List<ContentItem> content
    ) {
        public static Message user(List<ContentItem> content) {
            return new Message("user", content);
        }
    }

    /**
     * Anthropic 서버 사이드 도구 정의.
     *
     * web_search_20250305 는 Claude 가 자율적으로 검색을 수행하고 결과를 응답에 통합하는 서버 사이드 도구.
     * 모델이 검색이 필요한지 직접 판단하며, 검색 횟수는 max_uses 로 제한할 수 있다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            String type,
            String name,
            @JsonProperty("max_uses") Integer maxUses
    ) {
        public static Tool webSearch(int maxUses) {
            return new Tool("web_search_20250305", "web_search", maxUses);
        }
    }

    /**
     * 메시지 컨텐츠 블록.
     * type 에 따라 text/image 중 하나의 필드만 채워진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentItem(
            String type,
            String text,
            ImageSource source
    ) {
        public static ContentItem text(String text) {
            return new ContentItem("text", text, null);
        }

        public static ContentItem imageUrl(String url) {
            return new ContentItem("image", null, new ImageSource("url", url));
        }
    }

    public record ImageSource(
            String type,
            String url
    ) {
    }
}
