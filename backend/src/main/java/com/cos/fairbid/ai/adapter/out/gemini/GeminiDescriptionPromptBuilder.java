package com.cos.fairbid.ai.adapter.out.gemini;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest.Content;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest.GenerationConfig;
import com.cos.fairbid.ai.adapter.out.gemini.dto.GeminiGenerateRequest.Part;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 설명 전용 Gemini 프롬프트 빌더 (SPEC §19 옵션 B).
 *
 * <p>차이점 대비 {@link GeminiPromptBuilder}:</p>
 * <ul>
 *   <li>이미지 전달 없음 — phase1 의 {@link ProductAnalysis} 만 사용</li>
 *   <li>JSON 아닌 Markdown 문자열 출력 ({@code responseMimeType} 미지정)</li>
 *   <li>시스템 프롬프트에 hidden_value 강조 포함 (스모크 결과 20% 미달 보강)</li>
 * </ul>
 */
@Slf4j
@Component
public class GeminiDescriptionPromptBuilder {

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/auction-assist-description-gemini.txt";

    private final GeminiDescriptionProperties properties;

    private String systemPrompt;

    public GeminiDescriptionPromptBuilder(GeminiDescriptionProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void loadSystemPrompt() {
        this.systemPrompt = loadResource(SYSTEM_PROMPT_RESOURCE);
        log.info("Gemini description prompt loaded - {} chars", systemPrompt.length());
    }

    private String loadResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("설명 프롬프트 로드 실패: " + path, e);
        }
    }

    public GeminiGenerateRequest build(
            AiAssistCommand command,
            ProductAnalysis analysis,
            SuggestedPrices suggestedPrices,
            List<GuardrailViolation> retryViolations
    ) {
        String userText = buildUserText(command, analysis, suggestedPrices);

        List<Part> userParts;
        if (retryViolations == null || retryViolations.isEmpty()) {
            userParts = List.of(Part.text(userText));
        } else {
            userParts = List.of(
                    Part.text(userText),
                    Part.text(buildRetryFeedback(retryViolations))
            );
        }

        return new GeminiGenerateRequest(
                Content.system(systemPrompt),
                List.of(Content.user(userParts)),
                GenerationConfig.textOutput(properties.getMaxTokens())
        );
    }

    private String buildUserText(
            AiAssistCommand command,
            ProductAnalysis analysis,
            SuggestedPrices suggestedPrices
    ) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("다음 상품의 마케팅 설명 본문만 Markdown 으로 작성해주세요.\n\n");
        sb.append("## 상품 정보\n");
        sb.append("- 상품명: ").append(analysis.productName()).append('\n');
        sb.append("- 등급: ").append(analysis.grade()).append("급\n");
        if (analysis.gradeReason() != null && !analysis.gradeReason().isBlank()) {
            sb.append("- 등급 근거: ").append(analysis.gradeReason()).append('\n');
        }
        if (command.category() != null) {
            sb.append("- 카테고리: ").append(command.category().name()).append('\n');
        }
        if (command.memo() != null && !command.memo().isBlank()) {
            sb.append("- 사용자 memo:\n").append(command.memo()).append('\n');
        }

        if (suggestedPrices != null) {
            sb.append("\n## 참고 가격대 (본문 숫자 노출 금지)\n");
            sb.append("- low: ").append(suggestedPrices.low()).append("원\n");
            sb.append("- mid: ").append(suggestedPrices.mid()).append("원\n");
            sb.append("- high: ").append(suggestedPrices.high()).append("원\n");
            sb.append("가격 숫자를 본문에 직접 쓰지 말고, 체감 가성비 정도로만 참고하세요.\n");
        }

        sb.append("\n## 작성 지시\n");
        sb.append("- 출력은 시스템 프롬프트 규격의 **Markdown 본문 문자열만**. JSON 금지.\n");
        sb.append("- 마케터 체크리스트 5가지 모두 충족. 특히 3번 **숨은 가치(hidden_value)** 를 1개 이상 반드시 포함.\n");
        sb.append("- memo 에 없는 외관 상태/사용감/연식은 절대 추측 금지.");
        return sb.toString();
    }

    private String buildRetryFeedback(List<GuardrailViolation> retryViolations) {
        StringBuilder feedback = new StringBuilder(128);
        feedback.append("\n[이전 응답 검증 결과]\n");
        feedback.append("아래 문제가 발견되어 다시 생성합니다:\n");
        for (GuardrailViolation v : retryViolations) {
            feedback.append("- ").append(v.message()).append('\n');
        }
        feedback.append("\n위 문제를 수정해 본문 Markdown 만 다시 출력해주세요.");
        return feedback.toString();
    }
}
