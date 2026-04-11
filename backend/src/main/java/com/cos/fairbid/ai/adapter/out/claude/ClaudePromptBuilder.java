package com.cos.fairbid.ai.adapter.out.claude;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest;
import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest.ContentItem;
import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest.Message;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * Claude Messages API 요청을 조립한다.
 *
 * v2 2단계 호출 구조:
 * - 1차 (Phase1): 이미지 + memo → 상품 식별 + 등급 판정 + 검색 키워드
 * - 2차 (Phase2): 1차 결과 + 검색 결과 → 가격 산정 + 상품 설명 생성
 *
 * 두 프롬프트 모두 cache_control: ephemeral 로 Prompt Caching 적용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudePromptBuilder {

    private static final String PHASE1_PROMPT_RESOURCE = "prompts/auction-assist-phase1.txt";
    private static final String PHASE2_PROMPT_RESOURCE = "prompts/auction-assist-system.txt";

    private final AnthropicProperties properties;

    private String phase1Prompt;
    private String phase2Prompt;

    @PostConstruct
    void loadSystemPrompt() {
        this.phase1Prompt = loadResource(PHASE1_PROMPT_RESOURCE);
        this.phase2Prompt = loadResource(PHASE2_PROMPT_RESOURCE);
        log.info("Claude prompts loaded - phase1: {} chars, phase2: {} chars",
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

    /**
     * 1차 호출 요청을 조립한다.
     * 이미지 + memo 를 보고 상품명, 등급, 검색 키워드를 출력하게 한다.
     */
    public ClaudeMessageRequest buildPhase1(AiAssistCommand command) {
        Object system = List.of(ClaudeMessageRequest.SystemBlock.cached(phase1Prompt));

        List<ContentItem> userContent = new ArrayList<>(command.imageUrls().size() + 1);
        for (String imageUrl : command.imageUrls()) {
            userContent.add(ContentItem.imageUrl(imageUrl));
        }
        userContent.add(ContentItem.text(buildPhase1Text(command)));

        return new ClaudeMessageRequest(
                properties.getModel(),
                properties.getMaxTokens(),
                system,
                List.of(Message.user(userContent)),
                null  // 1차는 도구 불필요
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

    /**
     * 2차 호출 요청을 조립한다.
     * 1차 결과(상품명, 등급, 등급 근거) + 검색 결과 리스트를 보고
     * 최종 추천가(low/mid/high) + 상품 설명을 생성한다.
     *
     * @param command     원본 커맨드 (이미지, memo, category)
     * @param productName 1차에서 식별한 상품명
     * @param grade       1차에서 판정한 등급 (S/A/B/C/D)
     * @param gradeReason 1차에서 판정한 등급 근거
     * @param priceItems  네이버 검색 결과 (null 이면 검색 없이 추론)
     * @param retryViolations 재시도 시 이전 위반 항목 (null 이면 첫 시도)
     */
    public ClaudeMessageRequest buildPhase2(
            AiAssistCommand command,
            String productName,
            String grade,
            String gradeReason,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    ) {
        Object system = List.of(ClaudeMessageRequest.SystemBlock.cached(phase2Prompt));

        List<ContentItem> userContent = new ArrayList<>(command.imageUrls().size() + 1);
        for (String imageUrl : command.imageUrls()) {
            userContent.add(ContentItem.imageUrl(imageUrl));
        }
        userContent.add(ContentItem.text(buildPhase2Text(command, productName, grade, gradeReason, priceItems)));

        // 재시도 피드백 주입
        if (retryViolations != null && !retryViolations.isEmpty()) {
            StringBuilder feedback = new StringBuilder("\n[이전 응답 검증 결과]\n");
            feedback.append("아래 문제가 발견되어 다시 생성합니다:\n");
            for (GuardrailViolation v : retryViolations) {
                feedback.append("- ").append(v.message()).append('\n');
            }
            feedback.append("\n위 문제를 수정하여 다시 JSON을 생성해주세요.");
            userContent.add(ContentItem.text(feedback.toString()));
        }

        return new ClaudeMessageRequest(
                properties.getModel(),
                properties.getMaxTokens(),
                system,
                List.of(Message.user(userContent)),
                null
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

        // 1차 분석 결과
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

        // 검색 결과 리스트 (제목: 가격 형식)
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

}
