package com.cos.fairbid.ai.adapter.out.openai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.openai.dto.OpenAiChatRequest;
import com.cos.fairbid.ai.adapter.out.openai.dto.OpenAiChatRequest.ContentPart;
import com.cos.fairbid.ai.adapter.out.openai.dto.OpenAiChatRequest.Message;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * OpenAI Chat Completions 요청을 조립한다.
 *
 * Claude 용 프롬프트 리소스(phase1/phase2 txt)를 그대로 재사용한다 — 프롬프트 텍스트는 모델 독립.
 * 차이는 envelope 뿐:
 * - system 은 messages[0] 로 합침 (Claude 는 별도 system 필드)
 * - cache_control 없음 (OpenAI 는 자동 캐싱)
 * - tools 없음 (시세는 Naver API 로 외부에서 주입)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiPromptBuilder {

    private static final String PHASE1_PROMPT_RESOURCE = "prompts/auction-assist-phase1.txt";
    private static final String PHASE2_PROMPT_RESOURCE = "prompts/auction-assist-system.txt";

    private final OpenAiProperties properties;

    private String phase1Prompt;
    private String phase2Prompt;

    @PostConstruct
    public void loadSystemPrompt() {
        this.phase1Prompt = loadResource(PHASE1_PROMPT_RESOURCE);
        this.phase2Prompt = loadResource(PHASE2_PROMPT_RESOURCE);
        log.info("OpenAI prompts loaded - phase1: {} chars, phase2: {} chars",
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

    public OpenAiChatRequest buildPhase1(AiAssistCommand command) {
        List<ContentPart> userContent = new ArrayList<>(command.imageUrls().size() + 1);
        for (String imageUrl : command.imageUrls()) {
            userContent.add(ContentPart.imageUrl(imageUrl));
        }
        userContent.add(ContentPart.text(buildPhase1Text(command)));

        List<Message> messages = List.of(
                Message.system(phase1Prompt),
                Message.user(userContent)
        );

        return new OpenAiChatRequest(
                properties.getModel(),
                messages,
                // GPT-5.x reasoning 모델은 max_completion_tokens 안에 reasoning 토큰까지 포함되므로
                // 짧은 cap 을 두면 reasoning 만 하다 출력이 빈다. 모델 기본값 사용.
                null,
                OpenAiChatRequest.ResponseFormat.jsonObject()
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

    public OpenAiChatRequest buildPhase2(
            AiAssistCommand command,
            String productName,
            String grade,
            String gradeReason,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    ) {
        List<ContentPart> userContent = new ArrayList<>(command.imageUrls().size() + 2);
        for (String imageUrl : command.imageUrls()) {
            userContent.add(ContentPart.imageUrl(imageUrl));
        }
        userContent.add(ContentPart.text(buildPhase2Text(command, productName, grade, gradeReason, priceItems)));

        // 재시도 피드백 주입
        if (retryViolations != null && !retryViolations.isEmpty()) {
            StringBuilder feedback = new StringBuilder("\n[이전 응답 검증 결과]\n");
            feedback.append("아래 문제가 발견되어 다시 생성합니다:\n");
            for (GuardrailViolation v : retryViolations) {
                feedback.append("- ").append(v.message()).append('\n');
            }
            feedback.append("\n위 문제를 수정하여 다시 JSON을 생성해주세요.");
            userContent.add(ContentPart.text(feedback.toString()));
        }

        List<Message> messages = List.of(
                Message.system(phase2Prompt),
                Message.user(userContent)
        );

        return new OpenAiChatRequest(
                properties.getModel(),
                messages,
                null,
                OpenAiChatRequest.ResponseFormat.jsonObject()
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
}
