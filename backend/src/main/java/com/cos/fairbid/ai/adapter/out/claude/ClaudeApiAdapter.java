package com.cos.fairbid.ai.adapter.out.claude;

import java.util.List;

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

import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest;
import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageResponse;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.exception.AiGenerationFailedException;
import com.cos.fairbid.ai.domain.exception.AiServiceUnavailableException;
import com.cos.fairbid.ai.domain.exception.InvalidImageException;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * Anthropic Claude Messages API 어댑터 (AiClientPort 구현).
 *
 * - RestClient 동기 호출 (기존 OAuth 클라이언트와 동일 패턴)
 * - 프롬프트 캐싱은 ClaudePromptBuilder 가 담당하고, 여기서는 단순 호출/응답 매핑만 처리
 * - 외부 API 오류는 도메인 예외(AiServiceUnavailable / AiGenerationFailed / InvalidImage) 로 변환
 */
@Slf4j
@Component
public class ClaudeApiAdapter implements AiClientPort {

    private static final String MESSAGES_PATH = "/v1/messages";

    private final RestClient restClient;
    private final AnthropicProperties properties;
    private final ClaudePromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ClaudeApiAdapter(
            AnthropicProperties properties,
            ClaudePromptBuilder promptBuilder,
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
        ClaudeMessageRequest request = promptBuilder.buildPhase1(command);
        ClaudeMessageResponse response = null;
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
    public AiAssistResult generatePricing(
            AiAssistCommand command,
            ProductAnalysis analysis,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    ) {
        long startNanos = System.nanoTime();
        ClaudeMessageRequest request = promptBuilder.buildPhase2(
                command, analysis.productName(), analysis.grade(),
                analysis.gradeReason(), priceItems, retryViolations);
        ClaudeMessageResponse response = null;
        String outcome = "error";
        Throwable thrown = null;
        try {
            response = callApi(request);
            String rawText = extractText(response);
            AiAssistResult result = parseResult(rawText);
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

    /**
     * Anthropic Messages API 호출. HTTP/네트워크 오류는 도메인 예외로 변환한다.
     */
    private ClaudeMessageResponse callApi(ClaudeMessageRequest request) {
        try {
            ClaudeMessageResponse response = restClient.post()
                    .uri(MESSAGES_PATH)
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", properties.getAnthropicVersion())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            if (response == null) {
                log.warn("Anthropic 응답 본문이 null");
                throw AiGenerationFailedException.of();
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            // 타임아웃 / 연결 실패
            log.warn("Claude API 네트워크 오류: {}", ex.getMessage());
            throw AiServiceUnavailableException.withCause(ex);
        }
    }

    /**
     * HTTP 에러를 도메인 예외로 매핑.
     * - 5xx → 서비스 장애
     * - 4xx + image 관련 메시지 → 이미지 오류
     * - 그 외 4xx → 생성 실패
     */
    private RuntimeException mapHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        log.warn("Claude API HTTP 에러: status={}, body={}", status.value(), body);

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
        return lower.contains("image") || lower.contains("source") || lower.contains("url");
    }

    /**
     * 응답에서 최종 text 블록을 꺼낸다.
     *
     * 웹 서치 활성화 시 응답에는 여러 블록이 순차적으로 섞여 들어온다:
     *   server_tool_use → web_search_tool_result → text(중간 추론) → ... → text(최종 답변)
     *
     * 최종 JSON 응답은 마지막 text 블록에 들어 있으므로 첫 번째가 아닌 마지막 text 를 사용한다.
     */
    private String extractText(ClaudeMessageResponse response) {
        if (response.content() == null || response.content().isEmpty()) {
            log.warn("Claude 응답 content 가 비어있음");
            throw AiGenerationFailedException.of();
        }
        return response.content().stream()
                .filter(block -> "text".equals(block.type()))
                .map(ClaudeMessageResponse.ContentBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((first, second) -> second)
                .orElseThrow(() -> {
                    log.warn("Claude 응답에 text 블록이 없음");
                    return AiGenerationFailedException.of();
                });
    }

    /**
     * Claude 가 출력한 JSON 문자열을 파싱해 도메인 객체로 변환한다.
     *
     * Claude 는 항상 JSON 으로 응답하되, status 필드로 성공/실패를 구분한다:
     *   - status="success": suggestedPrices + generatedDescription 이 채워져 있음 → 정상 반환
     *   - status!="success" (need_more_info, mismatch, image_unreadable 등):
     *       userMessage 필드에 Claude 가 자기 말로 쓴 한국어 안내문이 있음 → 그대로 사용자에게 노출
     *
     * 내부 실패 사유(파싱 실패 / 필드 누락)는 로그에만 남긴다.
     */
    private AiAssistResult parseResult(String rawText) {
        String json = stripCodeFence(rawText).trim();
        ParsedPayload parsed;
        try {
            parsed = objectMapper.readValue(json, ParsedPayload.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Claude 응답 JSON 파싱 실패 - raw: {}", json);
            throw AiGenerationFailedException.withCause(ex);
        }

        if (parsed == null || parsed.status() == null || parsed.status().isBlank()) {
            log.warn("Claude 응답에 status 필드 없음 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        // 실패 분기 — Claude 가 직접 작성한 userMessage 를 사용자에게 그대로 전달
        if (!"success".equalsIgnoreCase(parsed.status())) {
            String userMessage = parsed.userMessage();
            if (userMessage == null || userMessage.isBlank()) {
                log.warn("Claude 실패 응답에 userMessage 누락 - status={}, raw={}", parsed.status(), json);
                throw AiGenerationFailedException.of();
            }
            log.info("Claude 실패 응답 - status={}, userMessage={}", parsed.status(), userMessage);
            throw AiGenerationFailedException.fromAi(userMessage);
        }

        // 성공 분기
        if (parsed.suggestedPrices() == null
                || parsed.suggestedPrices().low() == null
                || parsed.suggestedPrices().mid() == null
                || parsed.suggestedPrices().high() == null
                || parsed.generatedDescription() == null) {
            log.warn("Claude 성공 응답 필수 필드 누락 - raw: {}", json);
            throw AiGenerationFailedException.of();
        }

        SuggestedPrices prices = new SuggestedPrices(
                parsed.suggestedPrices().low(),
                parsed.suggestedPrices().mid(),
                parsed.suggestedPrices().high()
        );

        // confidence 처리 — null/빈 값이면 기본 "high"
        String confidence = parsed.confidence();
        if (confidence == null || confidence.isBlank()) {
            confidence = "high";
        }
        String confidenceReason = "low".equalsIgnoreCase(confidence) ? parsed.confidenceReason() : null;

        if ("low".equalsIgnoreCase(confidence)) {
            log.info("Claude 낮은 신뢰도 응답 - reason={}", confidenceReason);
        }

        return new AiAssistResult(prices, parsed.generatedDescription(), confidence, confidenceReason);
    }

    /**
     * 응답 텍스트가 ```json ... ``` 으로 감싸져 있으면 fence 를 제거한다.
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
     * 호출당 메트릭을 한 줄짜리 구조화 로그로 남긴다.
     *
     * 형식 (key=value, 공백 구분):
     *   AI_METRIC outcome=... latency_ms=... model=... input_tokens=... output_tokens=...
     *             cache_creation=... cache_read=... web_search_requests=... error_type=...
     *
     * - 누락 필드는 "-" 로 표기한다 (회귀 러너가 grep + split 으로 파싱).
     * - 응답 자체를 받지 못한 실패(네트워크/5xx)도 latency 와 outcome 은 항상 기록된다.
     * - JSON 응답의 status (success / need_more_info / mismatch / image_unreadable) 는
     *   parseResult 에서 별도로 로그하므로 여기서는 outcome(success/error) 만 본다.
     */
    private void recordCallMetric(
            ClaudeMessageResponse response,
            long elapsedMs,
            String outcome,
            Throwable thrown
    ) {
        Integer inputTokens = null;
        Integer outputTokens = null;
        Integer cacheCreation = null;
        Integer cacheRead = null;
        Integer webSearchRequests = null;
        String model = null;

        if (response != null) {
            model = response.model();
            ClaudeMessageResponse.Usage usage = response.usage();
            if (usage != null) {
                inputTokens = usage.inputTokens();
                outputTokens = usage.outputTokens();
                cacheCreation = usage.cacheCreationInputTokens();
                cacheRead = usage.cacheReadInputTokens();
                if (usage.serverToolUse() != null) {
                    webSearchRequests = usage.serverToolUse().webSearchRequests();
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
                nullToDash(cacheCreation),
                nullToDash(cacheRead),
                nullToDash(webSearchRequests),
                errorType
        );
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    /**
     * 1차 호출(상품 분석) 응답을 파싱한다.
     */
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

        // 실패 분기
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

    // ── 내부 파싱 DTO ──

    /** 1차 호출 응답 파싱용 */
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

    /** 2차 호출 응답 파싱용 — success / 실패 */
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
