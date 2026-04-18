package com.cos.fairbid.ai.benchmark.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ModelAdapterFactory 라우팅 로직 단위 테스트.
 *
 * <p>실제 어댑터 생성은 API 키가 필요하므로 여기선 "알 수 없는 모델 → 예외"와
 * "API 키 누락 → 예외" 경로만 검증한다. 프리픽스 매칭 가짓수가 많으므로 각 프리픽스가
 * 제대로 라우팅되는지(=API 키 누락으로 올바른 env 이름을 요구)도 확인한다.</p>
 */
@DisplayName("ModelAdapterFactory")
class ModelAdapterFactoryTest {

    private final ModelAdapterFactory factory = new ModelAdapterFactory(new ObjectMapper());

    @Test
    @DisplayName("알 수 없는 모델 프리픽스 → IllegalArgumentException")
    void rejectsUnknownModel() {
        assertThatThrownBy(() -> factory.build("llama-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown model");
    }

    @Test
    @DisplayName("claude* → ANTHROPIC_API_KEY 누락 시 명확한 에러")
    void claudeRequiresAnthropicKey() {
        // ANTHROPIC_API_KEY 환경변수가 없을 때만 의미 있는 테스트. CI에서 세팅돼 있으면 skip.
        if (System.getenv("ANTHROPIC_API_KEY") != null) {
            return;
        }
        assertThatThrownBy(() -> factory.build("claude-sonnet-4-5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    @DisplayName("gpt* → OPENAI_API_KEY 누락 시 명확한 에러")
    void openAiRequiresKey() {
        if (System.getenv("OPENAI_API_KEY") != null) {
            return;
        }
        assertThatThrownBy(() -> factory.build("gpt-5.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    @DisplayName("gemini* → GEMINI_API_KEY 누락 시 명확한 에러")
    void geminiRequiresKey() {
        if (System.getenv("GEMINI_API_KEY") != null) {
            return;
        }
        assertThatThrownBy(() -> factory.build("gemini-2.5-pro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    @DisplayName("프로바이더 별칭 — openai/anthropic/google")
    void providerAliasesRoutedCorrectly() {
        if (System.getenv("OPENAI_API_KEY") == null) {
            assertThatThrownBy(() -> factory.build("openai"))
                    .hasMessageContaining("OPENAI_API_KEY");
        }
        if (System.getenv("ANTHROPIC_API_KEY") == null) {
            assertThatThrownBy(() -> factory.build("anthropic"))
                    .hasMessageContaining("ANTHROPIC_API_KEY");
        }
        if (System.getenv("GEMINI_API_KEY") == null) {
            assertThatThrownBy(() -> factory.build("google"))
                    .hasMessageContaining("GEMINI_API_KEY");
        }
    }
}
