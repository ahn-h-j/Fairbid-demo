package com.cos.fairbid.ai.adapter.out.claude;

import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest;
import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest.ContentItem;
import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest.Message;
import com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageRequest.Tool;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude Messages API 요청을 조립한다.
 *
 * v1 설계:
 * - System Prompt 는 부팅 시 classpath 에서 한 번만 로드해 메모리에 보관한다.
 * - System 은 단순 문자열로 전송한다 (프롬프트 캐싱은 v2 에서 도입 예정).
 * - User message 는 호출마다 달라지는 부분(이미지 + 카테고리/제목/메모) 만 포함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudePromptBuilder {

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/auction-assist-system.txt";

    private final AnthropicProperties properties;

    private String systemPrompt;

    @PostConstruct
    void loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(SYSTEM_PROMPT_RESOURCE);
            this.systemPrompt = StreamUtils.copyToString(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8
            );
            log.info("Claude system prompt loaded ({} chars)", systemPrompt.length());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "AI 시스템 프롬프트 로드 실패: " + SYSTEM_PROMPT_RESOURCE, e);
        }
    }

    /**
     * Command 를 Anthropic Messages API 요청으로 변환한다.
     * 웹 서치가 활성화된 경우 tools 에 web_search 도구를 포함시킨다.
     */
    public ClaudeMessageRequest build(AiAssistCommand command) {
        List<Tool> tools = properties.isWebSearchEnabled()
                ? List.of(Tool.webSearch(properties.getWebSearchMaxUses()))
                : null;

        return new ClaudeMessageRequest(
                properties.getModel(),
                properties.getMaxTokens(),
                systemPrompt,
                List.of(Message.user(buildUserContent(command))),
                tools
        );
    }

    /**
     * User 메시지 컨텐츠 조립.
     * 이미지 블록을 먼저 배치한 뒤 텍스트 블록(카테고리/제목/메모)을 붙인다.
     */
    private List<ContentItem> buildUserContent(AiAssistCommand command) {
        List<ContentItem> content = new ArrayList<>(command.imageUrls().size() + 1);

        for (String imageUrl : command.imageUrls()) {
            content.add(ContentItem.imageUrl(imageUrl));
        }

        content.add(ContentItem.text(buildUserText(command)));
        return content;
    }

    private String buildUserText(AiAssistCommand command) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("다음 상품의 시작가 추천과 상품 설명을 생성해주세요.\n\n");

        if (command.category() != null) {
            sb.append("- 카테고리: ").append(command.category().name()).append('\n');
        } else {
            sb.append("- 카테고리: 미지정 (이미지와 상품 정보를 보고 추론하세요. ")
              .append("ELECTRONICS / FASHION / HOME / SPORTS / HOBBY / OTHER 중 하나)\n");
        }

        if (command.memo() != null && !command.memo().isBlank()) {
            sb.append("- 사용자 입력 정보:\n").append(command.memo()).append('\n');
        } else {
            sb.append("- 사용자 입력 정보: 없음 (이미지만으로 추론)\n");
        }

        sb.append('\n');
        sb.append("응답은 시스템 프롬프트에 정의된 JSON 스키마만 출력하세요. ");
        sb.append("사용자가 명시하지 않은 외관 상태/사용감/연식은 절대 추측해서 작성하지 마세요.");
        return sb.toString();
    }
}
