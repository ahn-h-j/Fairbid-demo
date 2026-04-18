package com.cos.fairbid.ai.adapter.out.gemini;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest.Content;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest.GenerationConfig;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest.Part;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.domain.exception.InvalidImageException;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * Gemini generateContent 요청을 조립한다.
 *
 * Claude 프롬프트 리소스(phase1/phase2 txt)를 그대로 재사용한다. 차이:
 * - system_instruction 필드 별도 (Claude 와 유사)
 * - 이미지는 base64 inline_data 로 전달 (임의 URL 직접 전달 불가) — 러너에서 네트워크 한 번 더 탐
 * - Prompt caching 은 별도 API (createCachedContent) 필요해서 v1 에서는 미사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiPromptBuilder {

    private static final String PHASE1_PROMPT_RESOURCE = "prompts/auction-assist-phase1.txt";
    private static final String PHASE2_PROMPT_RESOURCE = "prompts/auction-assist-system.txt";

    private final GeminiProperties properties;

    private String phase1Prompt;
    private String phase2Prompt;

    /** 이미지 다운로드용 클라이언트. 10초 커넥트, 응답은 body 받을 때 제한. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @PostConstruct
    public void loadSystemPrompt() {
        this.phase1Prompt = loadResource(PHASE1_PROMPT_RESOURCE);
        this.phase2Prompt = loadResource(PHASE2_PROMPT_RESOURCE);
        log.info("Gemini prompts loaded - phase1: {} chars, phase2: {} chars",
                phase1Prompt.length(), phase2Prompt.length());
    }

    private String loadResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("AI 프롬프트 로드 실패: " + path, e);
        }
    }

    // ── 1차 호출: 상품 식별 + 등급 판정 ──

    public GeminiGenerateRequest buildPhase1(AiAssistCommand command) {
        List<Part> userParts = new ArrayList<>(command.imageUrls().size() + 1);
        for (String imageUrl : command.imageUrls()) {
            userParts.add(downloadAsPart(imageUrl));
        }
        userParts.add(Part.text(buildPhase1Text(command)));

        return new GeminiGenerateRequest(
                Content.system(phase1Prompt),
                List.of(Content.user(userParts)),
                // Gemini 2.5 thinking 모델은 maxOutputTokens 안에 추론 토큰까지 포함되므로 명시 제한 없음(모델 기본값).
                GenerationConfig.jsonOutput(null)
        );
    }

    private String buildPhase1Text(AiAssistCommand command) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("다음 상품을 분석해주세요.\n\n");

        if (command.category() != null) {
            sb.append("- 카테고리: ").append(command.category().name()).append('\n');
        } else {
            sb.append("- 카테고리: 미지정 (이미지와 상품 정보를 보고 추론하세요. ")
                .append("ELECTRONICS / FASHION / HOME / SPORTS / HOBBY / OTHER 중 하나)\n");
        }

        if (command.memo() != null && !command.memo().isBlank()) {
            sb.append("- 사용자 입력 정보:\n").append(command.memo()).append('\n');
        } else {
            sb.append("- 사용자 입력 정보: 없음 (이미지만으로 분석)\n");
        }

        sb.append("\n응답은 시스템 프롬프트에 정의된 JSON 스키마만 출력하세요.");
        return sb.toString();
    }

    // ── 2차 호출: 가격 산정 + 설명 생성 ──

    public GeminiGenerateRequest buildPhase2(
            AiAssistCommand command,
            String productName,
            String grade,
            String gradeReason,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    ) {
        List<Part> userParts = new ArrayList<>(command.imageUrls().size() + 2);
        for (String imageUrl : command.imageUrls()) {
            userParts.add(downloadAsPart(imageUrl));
        }
        userParts.add(Part.text(buildPhase2Text(command, productName, grade, gradeReason, priceItems)));

        if (retryViolations != null && !retryViolations.isEmpty()) {
            StringBuilder feedback = new StringBuilder("\n[이전 응답 검증 결과]\n");
            feedback.append("아래 문제가 발견되어 다시 생성합니다:\n");
            for (GuardrailViolation v : retryViolations) {
                feedback.append("- ").append(v.message()).append('\n');
            }
            feedback.append("\n위 문제를 수정하여 다시 JSON을 생성해주세요.");
            userParts.add(Part.text(feedback.toString()));
        }

        return new GeminiGenerateRequest(
                Content.system(phase2Prompt),
                List.of(Content.user(userParts)),
                GenerationConfig.jsonOutput(null)
        );
    }

    private String buildPhase2Text(
            AiAssistCommand command,
            String productName,
            String grade,
            String gradeReason,
            List<PriceItem> priceItems
    ) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("다음 상품의 시작가 추천과 상품 설명을 생성해주세요.\n\n");

        sb.append("## 상품 분석 결과\n");
        sb.append("- 상품명: ").append(productName).append('\n');
        sb.append("- 등급: ").append(grade).append("급\n");
        sb.append("- 등급 근거: ").append(gradeReason).append('\n');

        if (command.category() != null) {
            sb.append("- 카테고리: ").append(command.category().name()).append('\n');
        }

        if (command.memo() != null && !command.memo().isBlank()) {
            sb.append("- 사용자 입력 정보:\n").append(command.memo()).append('\n');
        }

        if (priceItems != null && !priceItems.isEmpty()) {
            List<PriceItem> shopItems = priceItems.stream()
                    .filter(i -> i.description() == null)
                    .toList();
            List<PriceItem> cafeItems = priceItems.stream()
                    .filter(i -> i.description() != null)
                    .toList();

            if (!shopItems.isEmpty()) {
                sb.append("\n## 검색 결과 — 신품\n");
                sb.append("관련 없는 항목(부품, 액세서리, 다른 수량 등)은 무시하세요.\n");
                for (PriceItem item : shopItems) {
                    sb.append("- ").append(item.title()).append(": ")
                      .append(String.format("%,d원", item.lprice())).append('\n');
                }
            }

            if (!cafeItems.isEmpty()) {
                sb.append("\n## 검색 결과 — 중고 거래글\n");
                sb.append("아래는 중고거래 카페의 실 거래글입니다. 실 거래가를 참고하세요.\n");
                for (PriceItem item : cafeItems) {
                    sb.append("- [").append(item.mallName()).append("] ")
                      .append(item.title()).append('\n');
                    sb.append("  ").append(item.description()).append('\n');
                }
            }
        }

        sb.append('\n');
        sb.append("등급별 보정 범위를 적용해 가격을 산정하세요. ");
        sb.append("응답은 시스템 프롬프트에 정의된 JSON 스키마만 출력하세요. ");
        sb.append("사용자가 명시하지 않은 외관 상태/사용감/연식은 절대 추측해서 작성하지 마세요.");
        return sb.toString();
    }

    /**
     * HTTP(S) URL 에서 이미지를 다운로드해 Base64 Part 로 변환한다.
     * Gemini inline_data 는 최대 20MB 까지 수용하지만, 대부분 케이스는 1MB 이하라 단순 bytes 로 처리.
     *
     * - mime type 은 응답 Content-Type 에서 추출, 없으면 확장자로 추론, 그래도 없으면 image/jpeg 기본값
     * - 다운로드 실패/비이미지 응답은 InvalidImageException 으로 변환 (어댑터에서 도메인 예외로 재-매핑)
     */
    private Part downloadAsPart(String imageUrl) {
        try {
            // Wikipedia 등 일부 호스트가 Java HttpClient 기본 UA 를 차단하므로 명시적 UA 지정
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "FairBid-AiAssist/1.0 (+https://github.com/fairbid)")
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                log.warn("이미지 다운로드 실패 url={} status={}", imageUrl, response.statusCode());
                throw InvalidImageException.of();
            }
            String mimeType = response.headers().firstValue("Content-Type")
                    .map(ct -> ct.split(";")[0].trim())
                    .filter(ct -> ct.startsWith("image/"))
                    .orElse(guessMimeFromUrl(imageUrl));
            String base64 = Base64.getEncoder().encodeToString(response.body());
            return Part.image(mimeType, base64);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw InvalidImageException.of();
        } catch (IOException e) {
            log.warn("이미지 다운로드 네트워크 오류 url={}: {}", imageUrl, e.getMessage());
            throw InvalidImageException.of();
        }
    }

    private String guessMimeFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
