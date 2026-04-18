package com.cos.fairbid.ai.benchmark.runner;

import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.cos.fairbid.ai.adapter.out.claude.AnthropicProperties;
import com.cos.fairbid.ai.adapter.out.claude.ClaudeApiAdapter;
import com.cos.fairbid.ai.adapter.out.claude.ClaudePromptBuilder;
import com.cos.fairbid.ai.adapter.out.gemini.GeminiApiAdapter;
import com.cos.fairbid.ai.adapter.out.gemini.GeminiPromptBuilder;
import com.cos.fairbid.ai.adapter.out.gemini.GeminiProperties;
import com.cos.fairbid.ai.adapter.out.openai.OpenAiApiAdapter;
import com.cos.fairbid.ai.adapter.out.openai.OpenAiPromptBuilder;
import com.cos.fairbid.ai.adapter.out.openai.OpenAiProperties;
import com.cos.fairbid.ai.application.port.out.AiClientPort;

/**
 * 모델 식별자 문자열을 실제 {@link AiClientPort} 구현체로 변환하는 팩토리.
 *
 * <p>Spring {@code @ConditionalOnProperty(ai.provider)} 를 우회하여 여러 모델의 어댑터를
 * 동시에 인스턴스화할 수 있게 한다. 기존 {@code AiBaselineRunnerTest}의 수동 와이어링과
 * 동일한 패턴을 재사용한다.</p>
 *
 * <h3>모델 식별자 해석 규칙</h3>
 * <ul>
 *   <li>{@code claude} → 기본 Claude 모델 (ANTHROPIC_MODEL 환경변수 있으면 그 값, 없으면 properties 기본값)</li>
 *   <li>{@code claude-sonnet-4-5}, {@code haiku-*}, {@code opus-*} → Claude, 전체 문자열이 모델 ID</li>
 *   <li>{@code openai} → 기본 OpenAI 모델 (OPENAI_MODEL 환경변수 있으면 그 값)</li>
 *   <li>{@code gpt-5.1}, {@code gpt-4o-mini} 등 → OpenAI, 전체 문자열이 모델 ID</li>
 *   <li>{@code gemini} → 기본 Gemini 모델 (GEMINI_MODEL 환경변수)</li>
 *   <li>{@code gemini-2.5-pro} 등 → Gemini, 전체 문자열이 모델 ID</li>
 * </ul>
 *
 * <p>API 키는 환경변수에서 읽는다 ({@code ANTHROPIC_API_KEY}, {@code OPENAI_API_KEY},
 * {@code GEMINI_API_KEY}). 누락 시 {@link IllegalStateException}.</p>
 */
public final class ModelAdapterFactory {

    /** 어댑터 + 해석된 모델 라벨 (리포트에 기록용). */
    public record ModelAdapter(AiClientPort adapter, String modelLabel) {}

    private final ObjectMapper objectMapper;

    public ModelAdapterFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param modelName {@code BENCHMARK_MODELS} 의 한 요소
     * @throws IllegalArgumentException 모델 프리픽스를 알 수 없을 때
     * @throws IllegalStateException 필요한 API 키 환경변수 누락 시
     */
    public ModelAdapter build(String modelName) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (isClaude(lower)) {
            return buildClaude(isGenericProviderName(modelName, "claude", "anthropic") ? null : modelName);
        }
        if (isOpenAi(lower)) {
            return buildOpenAi(isGenericProviderName(modelName, "openai") ? null : modelName);
        }
        if (isGemini(lower)) {
            return buildGemini(isGenericProviderName(modelName, "gemini", "google") ? null : modelName);
        }
        throw new IllegalArgumentException("Unknown model: " + modelName
                + " (expected one of: claude*, gpt*/openai*, gemini*)");
    }

    private static boolean isClaude(String lower) {
        return lower.startsWith("claude")
                || lower.startsWith("anthropic")
                || lower.startsWith("sonnet")
                || lower.startsWith("haiku")
                || lower.startsWith("opus");
    }

    private static boolean isOpenAi(String lower) {
        return lower.startsWith("gpt") || lower.startsWith("openai") || lower.startsWith("o1");
    }

    private static boolean isGemini(String lower) {
        return lower.startsWith("gemini") || lower.startsWith("google");
    }

    private static boolean isGenericProviderName(String name, String... aliases) {
        for (String a : aliases) {
            if (a.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private ModelAdapter buildClaude(String modelIdOverride) {
        String apiKey = requireEnv("ANTHROPIC_API_KEY");
        AnthropicProperties props = new AnthropicProperties();
        props.setApiKey(apiKey);
        // 벤치마크 중에는 웹서치 비활성 — 비용/변동성 제거, 네이버 검색 결과로만 평가.
        props.setWebSearchEnabled(false);

        String modelId = modelIdOverride != null
                ? modelIdOverride
                : defaultIfBlank(System.getenv("ANTHROPIC_MODEL"), props.getModel());
        props.setModel(modelId);

        ClaudePromptBuilder pb = new ClaudePromptBuilder(props);
        pb.loadSystemPrompt();
        return new ModelAdapter(
                new ClaudeApiAdapter(props, pb, objectMapper),
                modelId);
    }

    private ModelAdapter buildOpenAi(String modelIdOverride) {
        String apiKey = requireEnv("OPENAI_API_KEY");
        OpenAiProperties props = new OpenAiProperties();
        props.setApiKey(apiKey);

        String modelId = modelIdOverride != null
                ? modelIdOverride
                : defaultIfBlank(System.getenv("OPENAI_MODEL"), props.getModel());
        props.setModel(modelId);

        OpenAiPromptBuilder pb = new OpenAiPromptBuilder(props);
        pb.loadSystemPrompt();
        return new ModelAdapter(
                new OpenAiApiAdapter(props, pb, objectMapper),
                modelId);
    }

    private ModelAdapter buildGemini(String modelIdOverride) {
        String apiKey = requireEnv("GEMINI_API_KEY");
        GeminiProperties props = new GeminiProperties();
        props.setApiKey(apiKey);

        String modelId = modelIdOverride != null
                ? modelIdOverride
                : defaultIfBlank(System.getenv("GEMINI_MODEL"), props.getModel());
        props.setModel(modelId);

        GeminiPromptBuilder pb = new GeminiPromptBuilder(props);
        pb.loadSystemPrompt();
        return new ModelAdapter(
                new GeminiApiAdapter(props, pb, objectMapper),
                modelId);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " env is required for this benchmark model");
        }
        return value;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
