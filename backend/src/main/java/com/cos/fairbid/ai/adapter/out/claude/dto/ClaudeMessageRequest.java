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
        Object system,
        List<Message> messages,
        List<Tool> tools
) {

    /**
     * System prompt 블록 (Prompt Caching 지원).
     *
     * - system 필드에 String 대신 {@code List<SystemBlock>} 을 넣으면 블록별로
     *   cache_control 을 부여할 수 있다.
     * - Anthropic API 는 system 이 String 이든 Array 이든 둘 다 수용한다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SystemBlock(
            String type,
            String text,
            @JsonProperty("cache_control") CacheControl cacheControl
    ) {
        /** cache_control: ephemeral — 5분 TTL 캐시 */
        public static SystemBlock cached(String text) {
            return new SystemBlock("text", text, new CacheControl("ephemeral"));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CacheControl(String type) {
    }

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
            @JsonProperty("max_uses") Integer maxUses,
            @JsonProperty("allowed_domains") List<String> allowedDomains
    ) {
        public static Tool webSearch(int maxUses) {
            return new Tool("web_search_20250305", "web_search", maxUses, null);
        }

        /**
         * web_search 도구를 도메인 화이트리스트와 함께 생성한다.
         * 시세 신뢰도가 높은 도메인만 검색하게 해 검색 결과 페이지 토큰을 줄이고 노이즈를 거른다.
         */
        public static Tool webSearch(int maxUses, List<String> allowedDomains) {
            return new Tool("web_search_20250305", "web_search", maxUses,
                    allowedDomains == null || allowedDomains.isEmpty() ? null : allowedDomains);
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
