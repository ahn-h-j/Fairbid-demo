package com.cos.fairbid.ai.adapter.out.gemini;

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

import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateResponse;
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
 * Google Gemini generateContent API 어댑터 (AiClientPort 구현).
 *
 * ClaudeApiAdapter / OpenAiApiAdapter 와 동일한 계약.
 * 차이점:
 * - 엔드포인트: /v1beta/models/{model}:generateContent?key={apiKey}
 * - 응답 shape: candidates[0].content.parts[0].text
 * - 토큰 필드: usageMetadata.promptTokenCount / candidatesTokenCount / cachedContentTokenCount
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiApiAdapter implements AiClientPort {

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final GeminiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public GeminiApiAdapter(
            GeminiProperties properties,
            GeminiPromptBuilder promptBuilder,
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

    @Override
    public ProductAnalysis analyzeProduct(AiAssistCommand command) {
        long startNanos = System.nanoTime();
        GeminiGenerateRequest request = promptBuilder.buildPhase1(command);
        GeminiGenerateResponse response = null;
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
        GeminiGenerateRequest request = promptBuilder.buildPhase2(
                command, analysis.productName(), analysis.grade(),
                analysis.gradeReason(), priceItems, retryViolations);
        GeminiGenerateResponse response = null;
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

    private GeminiGenerateResponse callApi(GeminiGenerateRequest request) {
        String path = "/v1beta/models/" + properties.getModel() + ":generateContent";
        try {
            GeminiGenerateResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam("key", properties.getApiKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            if (response == null) {
                log.warn("Gemini 응답 본문이 null");
                throw AiGenerationFailedException.of();
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Gemini API 네트워크 오류: {}", ex.getMessage());
            throw AiServiceUnavailableException.withCause(ex);
        }
    }

    private RuntimeException mapHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        log.warn("Gemini API HTTP 에러: status={}, body={}", status.value(), body);

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
        return lower.contains("image") || lower.contains("inline_data") || lower.contains("mime");
    }

    /**
     * 응답의 첫 candidate 에서 text part 를 추출한다.
     * Gemini 는 parts 에 여러 블록이 올 수 있지만 responseMimeType=json 설정 시 보통 단일 text 블록.
     */
    private String extractText(GeminiGenerateResponse response) {
        if (response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("Gemini 응답 candidates 비어있음");
            throw AiGenerationFailedException.of();
        }
        GeminiGenerateResponse.Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null
                || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            log.warn("Gemini 응답 content.parts 비어있음 - finishReason={}", candidate.finishReason());
            throw AiGenerationFailedException.of();
        }

        StringBuilder sb = new StringBuilder();
        for (GeminiGenerateResponse.Part part : candidate.content().parts()) {
            if (part.text() != null) {
                sb.append(part.text());
            }
        }
        String result = sb.toString();
        if (result.isBlank()) {
            log.warn("Gemini 응답 text 비어있음 - finishReason={}", candidate.finishReason());
            throw AiGenerationFailedException.of();
        }
        return result;
    }

    private PricingResult parseResult(String rawText) {
        String json = stripCodeFence(rawText).trim();
        ParsedPayload parsed;
        try {
            parsed = objectMapper.readValue(json, ParsedPayload.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Gemini 응답 JSON 파싱 실패 - raw: {}", json);
            throw AiGenerationFailedException.withCause(ex);
        }

        if (parsed == null || parsed.status() == null || parsed.status().isBlank()) {
            log.warn("Gemini 응답에 status 필드 없음 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        if (!"success".equalsIgnoreCase(parsed.status())) {
            String userMessage = parsed.userMessage();
            if (userMessage == null || userMessage.isBlank()) {
                log.warn("Gemini 실패 응답에 userMessage 누락 - status={}", parsed.status());
                throw AiGenerationFailedException.of();
            }
            log.info("Gemini 실패 응답 - status={}, userMessage={}", parsed.status(), userMessage);
            throw AiGenerationFailedException.fromAi(userMessage);
        }

        if (parsed.suggestedPrices() == null
                || parsed.suggestedPrices().low() == null
                || parsed.suggestedPrices().mid() == null
                || parsed.suggestedPrices().high() == null) {
            log.warn("Gemini 성공 응답 필수 필드 누락 - raw: {}", json);
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

        return new PricingResult(prices, confidence, confidenceReason);
    }

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
     * Claude/OpenAI 어댑터와 동일한 AI_METRIC 포맷.
     *
     * 필드 매핑:
     * - input_tokens  = usageMetadata.promptTokenCount
     * - output_tokens = usageMetadata.candidatesTokenCount
     * - cache_read    = usageMetadata.cachedContentTokenCount (implicit cache, 2.5 이상 일부 지원)
     * - cache_creation / web_search_requests: 미사용 → "-"
     */
    private void recordCallMetric(
            GeminiGenerateResponse response,
            long elapsedMs,
            String outcome,
            Throwable thrown
    ) {
        Integer inputTokens = null;
        Integer outputTokens = null;
        Integer cacheRead = null;
        String model = null;

        if (response != null) {
            model = response.modelVersion();
            GeminiGenerateResponse.UsageMetadata usage = response.usageMetadata();
            if (usage != null) {
                inputTokens = usage.promptTokenCount();
                outputTokens = usage.candidatesTokenCount();
                cacheRead = usage.cachedContentTokenCount();
            }
        }

        String errorType = thrown == null ? "-" : thrown.getClass().getSimpleName();

        log.info(
                "AI_METRIC outcome={} latency_ms={} model={} input_tokens={} output_tokens={} "
                        + "cache_creation={} cache_read={} web_search_requests={} error_type={}",
                outcome, elapsedMs, nullToDash(model),
                nullToDash(inputTokens), nullToDash(outputTokens),
                "-", nullToDash(cacheRead), "-", errorType
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedPhase1(
            String status, String productName, String grade, String gradeReason,
            String searchKeyword, String productKey, String userMessage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedPayload(
            String status, String confidence, String confidenceReason,
            ParsedPrices suggestedPrices, String generatedDescription, String userMessage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ParsedPrices(Long low, Long mid, Long high) {
    }
}
