package com.cos.fairbid.ai.adapter.out.openai;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.openai.dto.OpenAiChatRequest;
import com.cos.fairbid.ai.adapter.out.openai.dto.OpenAiChatResponse;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.domain.PricingResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.exception.AiGenerationFailedException;
import com.cos.fairbid.ai.domain.exception.AiServiceUnavailableException;
import com.cos.fairbid.ai.domain.exception.InvalidImageException;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * OpenAI Chat Completions API 어댑터 (AiClientPort 구현).
 *
 * ClaudeApiAdapter 와 동일한 계약을 따른다:
 * - RestClient 동기 호출
 * - 외부 API 오류 → 도메인 예외 변환
 * - AI_METRIC 로그로 per-call 메트릭 기록 (베이스라인 러너가 grep 파싱)
 *
 * 차이점:
 * - Chat Completions 응답 shape: choices[0].message.content
 * - 토큰 필드명: prompt_tokens / completion_tokens / prompt_tokens_details.cached_tokens
 * - web_search 미사용 (시세는 Naver API 로 외부에서 주입)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiApiAdapter implements AiClientPort {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final OpenAiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public OpenAiApiAdapter(
            OpenAiProperties properties,
            OpenAiPromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    // ── 2단계 호출 ──

    @Override
    public ProductAnalysis analyzeProduct(AiAssistCommand command) {
        long startNanos = System.nanoTime();
        OpenAiChatRequest request = promptBuilder.buildPhase1(command);
        OpenAiChatResponse response = null;
        String outcome = "error";
        Throwable thrown = null;
        try {
            response = callApi(request);
            String rawText = extractText(response);
            ProductAnalysis analysis = parsePhase1Result(rawText);
            outcome = "success";
            return analysis;
        } catch (RuntimeException e) {
            thrown = e;
            throw e;
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            recordCallMetric(response, elapsedMs, outcome, thrown);
        }
    }

    @Override
    public PricingResult generatePricing(
            AiAssistCommand command,
            ProductAnalysis analysis,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    ) {
        long startNanos = System.nanoTime();
        OpenAiChatRequest request = promptBuilder.buildPhase2(
                command, analysis.productName(), analysis.grade(),
                analysis.gradeReason(), priceItems, retryViolations);
        OpenAiChatResponse response = null;
        String outcome = "error";
        Throwable thrown = null;
        try {
            response = callApi(request);
            String rawText = extractText(response);
            PricingResult result = parseResult(rawText);
            outcome = "success";
            return result;
        } catch (RuntimeException e) {
            thrown = e;
            throw e;
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            recordCallMetric(response, elapsedMs, outcome, thrown);
        }
    }

    private OpenAiChatResponse callApi(OpenAiChatRequest request) {
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null) {
                log.warn("OpenAI 응답 본문이 null");
                throw AiGenerationFailedException.of();
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            log.warn("OpenAI API 네트워크 오류: {}", ex.getMessage());
            throw AiServiceUnavailableException.withCause(ex);
        }
    }

    private RuntimeException mapHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        log.warn("OpenAI API HTTP 에러: status={}, body={}", status.value(), body);

        if (status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return AiServiceUnavailableException.withCause(ex);
        }

        if (status.is4xxClientError() && isImageRelatedError(body)) {
            return InvalidImageException.of();
        }

        return AiGenerationFailedException.withCause(ex);
    }

    private boolean isImageRelatedError(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("image") || lower.contains("image_url") || lower.contains("invalid_image");
    }

    /**
     * 응답에서 assistant 메시지의 content 문자열을 꺼낸다.
     * Chat Completions 은 항상 choices[0].message.content 에 최종 텍스트가 들어온다.
     */
    private String extractText(OpenAiChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            log.warn("OpenAI 응답 choices 가 비어있음");
            throw AiGenerationFailedException.of();
        }
        OpenAiChatResponse.Choice choice = response.choices().get(0);
        if (choice.message() == null
                || choice.message().content() == null
                || choice.message().content().isBlank()) {
            log.warn("OpenAI 응답 message.content 가 비어있음 - finish_reason={}",
                    choice.finishReason());
            throw AiGenerationFailedException.of();
        }
        return choice.message().content();
    }

    /**
     * 2차 응답 JSON 파싱. Claude 와 동일한 스키마 사용하되, SPEC §19 옵션 B 적용으로
     * {@code generatedDescription} 필드는 파싱만 하고 버린다.
     */
    private PricingResult parseResult(String rawText) {
        String json = stripCodeFence(rawText).trim();
        ParsedPayload parsed;
        try {
            parsed = objectMapper.readValue(json, ParsedPayload.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("OpenAI 응답 JSON 파싱 실패 - raw: {}", json);
            throw AiGenerationFailedException.withCause(ex);
        }

        if (parsed == null || parsed.status() == null || parsed.status().isBlank()) {
            log.warn("OpenAI 응답에 status 필드 없음 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        if (!"success".equalsIgnoreCase(parsed.status())) {
            String userMessage = parsed.userMessage();
            if (userMessage == null || userMessage.isBlank()) {
                log.warn("OpenAI 실패 응답에 userMessage 누락 - status={}, raw={}", parsed.status(), json);
                throw AiGenerationFailedException.of();
            }
            log.info("OpenAI 실패 응답 - status={}, userMessage={}", parsed.status(), userMessage);
            throw AiGenerationFailedException.fromAi(userMessage);
        }

        if (parsed.suggestedPrices() == null
                || parsed.suggestedPrices().low() == null
                || parsed.suggestedPrices().mid() == null
                || parsed.suggestedPrices().high() == null) {
            log.warn("OpenAI 성공 응답 필수 필드 누락 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        SuggestedPrices prices = new SuggestedPrices(
                parsed.suggestedPrices().low(),
                parsed.suggestedPrices().mid(),
                parsed.suggestedPrices().high()
        );

        String confidence = parsed.confidence();
        if (confidence == null || confidence.isBlank()) {
            confidence = "high";
        }
        String confidenceReason = "low".equalsIgnoreCase(confidence) ? parsed.confidenceReason() : null;

        if ("low".equalsIgnoreCase(confidence)) {
            log.info("OpenAI 낮은 신뢰도 응답 - reason={}", confidenceReason);
        }

        return new PricingResult(prices, confidence, confidenceReason);
    }

    /**
     * json_object 모드에서는 코드펜스가 거의 나오지 않지만 방어적으로 제거한다.
     */
    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String withoutOpener = trimmed.substring(firstNewline + 1);
        int closingFence = withoutOpener.lastIndexOf("```");
        if (closingFence < 0) {
            return withoutOpener;
        }
        return withoutOpener.substring(0, closingFence);
    }

    /**
     * Claude 어댑터와 동일한 AI_METRIC 포맷으로 기록한다. 러너가 양쪽을 동일하게 파싱한다.
     *
     * 필드 매핑:
     * - input_tokens  = usage.prompt_tokens
     * - output_tokens = usage.completion_tokens
     * - cache_read    = usage.prompt_tokens_details.cached_tokens (없으면 0)
     * - cache_creation = "-" (OpenAI 는 캐시 생성 별도 과금/필드 없음)
     * - web_search_requests = "-" (미사용)
     */
    private void recordCallMetric(
            OpenAiChatResponse response,
            long elapsedMs,
            String outcome,
            Throwable thrown
    ) {
        Integer inputTokens = null;
        Integer outputTokens = null;
        Integer cacheRead = null;
        String model = null;

        if (response != null) {
            model = response.model();
            OpenAiChatResponse.Usage usage = response.usage();
            if (usage != null) {
                inputTokens = usage.promptTokens();
                outputTokens = usage.completionTokens();
                if (usage.promptTokensDetails() != null) {
                    cacheRead = usage.promptTokensDetails().cachedTokens();
                }
            }
        }

        String errorType = thrown == null ? "-" : thrown.getClass().getSimpleName();

        log.info(
                "AI_METRIC outcome={} latency_ms={} model={} input_tokens={} output_tokens={} "
                        + "cache_creation={} cache_read={} web_search_requests={} error_type={}",
                outcome,
                elapsedMs,
                nullToDash(model),
                nullToDash(inputTokens),
                nullToDash(outputTokens),
                "-",
                nullToDash(cacheRead),
                "-",
                errorType
        );
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private ProductAnalysis parsePhase1Result(String rawText) {
        String json = stripCodeFence(rawText).trim();
        ParsedPhase1 parsed;
        try {
            parsed = objectMapper.readValue(json, ParsedPhase1.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Phase1 응답 JSON 파싱 실패 - raw: {}", json);
            throw AiGenerationFailedException.withCause(e);
        }

        if (parsed == null || parsed.status() == null || parsed.status().isBlank()) {
            log.warn("Phase1 응답에 status 필드 없음 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        if (!"success".equalsIgnoreCase(parsed.status())) {
            String userMessage = parsed.userMessage();
            if (userMessage == null || userMessage.isBlank()) {
                log.warn("Phase1 실패 응답에 userMessage 누락 - status={}", parsed.status());
                throw AiGenerationFailedException.of();
            }
            throw AiGenerationFailedException.fromAi(userMessage);
        }

        if (parsed.productName() == null || parsed.grade() == null || parsed.searchKeyword() == null) {
            log.warn("Phase1 성공 응답 필수 필드 누락 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        return new ProductAnalysis(
                parsed.productName(),
                parsed.grade(),
                parsed.gradeReason() != null ? parsed.gradeReason() : "",
                parsed.searchKeyword(),
                parsed.productKey() != null ? parsed.productKey() : ""
        );
    }

    // ── 내부 파싱 DTO (Claude 와 동일 스키마) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedPhase1(
            String status,
            String productName,
            String grade,
            String gradeReason,
            String searchKeyword,
            String productKey,
            String userMessage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedPayload(
            String status,
            String confidence,
            String confidenceReason,
            ParsedPrices suggestedPrices,
            String generatedDescription,
            String userMessage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedPrices(
            Long low,
            Long mid,
            Long high
    ) {
    }
}
