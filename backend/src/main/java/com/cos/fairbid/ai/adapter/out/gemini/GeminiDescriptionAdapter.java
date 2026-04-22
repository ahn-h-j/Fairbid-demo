package com.cos.fairbid.ai.adapter.out.gemini;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateResponse;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.DescriptionGeneratorPort;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.exception.AiGenerationFailedException;
import com.cos.fairbid.ai.domain.exception.AiServiceUnavailableException;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * Google Gemini 설명 전용 어댑터 (SPEC §19 옵션 B {@link DescriptionGeneratorPort} 구현).
 *
 * <p>{@link GeminiApiAdapter}(가격/벤치용, {@code ai.provider=gemini} 조건부) 와 별개로
 * 항상 빈으로 등록된다. Claude 가 가격을 내고 Gemini 가 설명을 내는 역할 분할 구조.</p>
 *
 * <p>특징:</p>
 * <ul>
 *   <li>이미지 재분석 없음. phase1({@link ProductAnalysis}) 결과만 사용</li>
 *   <li>JSON 아닌 Markdown 문자열 직접 반환</li>
 *   <li>메트릭 로그는 {@link GeminiApiAdapter} 포맷과 구분 위해 adapter=gemini-description 접미사 사용</li>
 * </ul>
 */
@Slf4j
@Component
public class GeminiDescriptionAdapter implements DescriptionGeneratorPort {

    private final RestClient restClient;
    private final GeminiDescriptionProperties properties;
    private final GeminiDescriptionPromptBuilder promptBuilder;

    public GeminiDescriptionAdapter(
            GeminiDescriptionProperties properties,
            GeminiDescriptionPromptBuilder promptBuilder
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
    }

    @Override
    public String generateDescription(
            AiAssistCommand command,
            ProductAnalysis analysis,
            SuggestedPrices suggestedPrices,
            List<GuardrailViolation> retryViolations
    ) {
        long startNanos = System.nanoTime();
        GeminiGenerateRequest request = promptBuilder.build(command, analysis, suggestedPrices, retryViolations);
        String outcome = "error";
        Throwable thrown = null;
        try {
            GeminiGenerateResponse response = callApi(request);
            String rawText = extractText(response);
            String description = rawText.trim();
            if (description.isBlank()) {
                log.warn("Gemini 설명 응답이 비어있음");
                throw AiGenerationFailedException.of();
            }
            outcome = "success";
            return description;
        } catch (RuntimeException e) {
            thrown = e;
            throw e;
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("AI_METRIC adapter=gemini-description model={} phase=description elapsed_ms={} outcome={}"
                            + (thrown != null ? " error={}" : ""),
                    properties.getModel(), elapsedMs, outcome,
                    thrown == null ? "" : thrown.getClass().getSimpleName());
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
                log.warn("Gemini 설명 응답 본문이 null");
                throw AiGenerationFailedException.of();
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Gemini 설명 API 네트워크 오류: {}", ex.getMessage());
            throw AiServiceUnavailableException.withCause(ex);
        }
    }

    private RuntimeException mapHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        log.warn("Gemini 설명 API HTTP 에러: status={}, body={}", status.value(), body);

        if (status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return AiServiceUnavailableException.withCause(ex);
        }
        return AiGenerationFailedException.withCause(ex);
    }

    private String extractText(GeminiGenerateResponse response) {
        if (response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("Gemini 설명 응답 candidates 비어있음");
            throw AiGenerationFailedException.of();
        }
        GeminiGenerateResponse.Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null
                || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            log.warn("Gemini 설명 응답 content.parts 비어있음 - finishReason={}", candidate.finishReason());
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
            log.warn("Gemini 설명 응답 text 비어있음 - finishReason={}", candidate.finishReason());
            throw AiGenerationFailedException.of();
        }
        return result;
    }
}
