package com.cos.fairbid.ai.adapter.out.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionQualityRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.HookRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PersonaRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.ReformatRule;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.golden.GoldenCaseLoader;
import com.cos.fairbid.ai.benchmark.runner.ModelAdapterFactory;
import com.cos.fairbid.ai.description_smoke.SmokeCaseSelector;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.auction.domain.Category;

/**
 * {@link GeminiDescriptionAdapter} 실측 테스트.
 *
 * <p>{@code DESCRIPTION_LIVE_TEST=true} + {@code GEMINI_API_KEY} + {@code ANTHROPIC_API_KEY}
 * 환경변수가 있을 때만 동작. SPEC §19 옵션 B 이슈 #91 완료 조건 검증용.</p>
 *
 * <p>기존 스모크 10건 케이스({@link SmokeCaseSelector})를 그대로 사용해
 * Claude phase1 분석 → Gemini phase2b 설명 생성 흐름을 재현한다. Claude phase2a(가격)는 비용/시간
 * 절감을 위해 스킵하고 SuggestedPrices 는 goldenCase.expected 에서 파생한다.</p>
 *
 * <p>집계: 케이스별 latency, 설명 길이, 가드레일 위반 수. 스모크 이전(70% 위반율) 대비 회귀 감시.</p>
 */
@EnabledIfEnvironmentVariable(named = "DESCRIPTION_LIVE_TEST", matches = "true")
class GeminiDescriptionAdapterLiveTest {

    @Test
    void generateDescription_liveTenCases() throws Exception {
        GeminiDescriptionProperties props = new GeminiDescriptionProperties();
        props.setApiKey(System.getenv("GEMINI_API_KEY"));
        String modelOverride = System.getenv("AI_DESCRIPTION_GEMINI_MODEL");
        if (modelOverride != null && !modelOverride.isBlank()) {
            props.setModel(modelOverride);
        }
        assertThat(props.getApiKey()).as("GEMINI_API_KEY 필요").isNotBlank();
        assertThat(System.getenv("ANTHROPIC_API_KEY")).as("ANTHROPIC_API_KEY 필요").isNotBlank();

        GeminiDescriptionPromptBuilder builder = new GeminiDescriptionPromptBuilder(props);
        builder.loadSystemPrompt();
        GeminiDescriptionAdapter adapter = new GeminiDescriptionAdapter(props, builder);

        ObjectMapper objectMapper = new ObjectMapper();
        ModelAdapterFactory factory = new ModelAdapterFactory(objectMapper);
        AiClientPort claude = factory.build("claude").adapter();

        DescriptionQualityRule qualityRule = new DescriptionQualityRule();
        HookRule hookRule = new HookRule();
        PersonaRule personaRule = new PersonaRule();
        ReformatRule reformatRule = new ReformatRule();

        List<GoldenCase> allCases = GoldenCaseLoader.loadFromClasspath("ai/golden/cases.jsonl");
        List<GoldenCase> selected = SmokeCaseSelector.select(allCases);

        // 서브셋 실행용 env 필터 (비교 측정 편의)
        String onlyRaw = System.getenv("LIVE_ONLY_IDS");
        if (onlyRaw != null && !onlyRaw.isBlank()) {
            java.util.Set<String> keep = java.util.Arrays.stream(onlyRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
            selected = selected.stream().filter(c -> keep.contains(c.id())).toList();
        }

        List<Row> rows = new ArrayList<>(selected.size());
        StringBuilder outputsFull = new StringBuilder();

        for (GoldenCase c : selected) {
            Category category = c.category() == null ? null : Category.valueOf(c.category());
            List<String> imageUrls = c.imageUrl() == null || c.imageUrl().isBlank()
                    ? List.of()
                    : List.of(c.imageUrl());
            AiAssistCommand phase1Command = new AiAssistCommand(category, c.memo(), imageUrls);

            ProductAnalysis analysis;
            try {
                analysis = claude.analyzeProduct(phase1Command);
            } catch (RuntimeException e) {
                rows.add(Row.failure(c.id(), "phase1_" + e.getClass().getSimpleName(), 0, 0, 0));
                continue;
            }

            // 이미지 빼고 Gemini 호출 (phase1 에서 이미 이미지 소비, 옵션 B 구조상 이미지 재분석 불필요)
            AiAssistCommand descCommand = new AiAssistCommand(category, c.memo(), List.of());
            SuggestedPrices prices = new SuggestedPrices(
                    c.expected().low(), c.expected().mid(), c.expected().high());

            long t0 = System.currentTimeMillis();
            String description;
            try {
                description = adapter.generateDescription(descCommand, analysis, prices, null);
            } catch (RuntimeException e) {
                rows.add(Row.failure(c.id(), "desc_" + e.getClass().getSimpleName(),
                        System.currentTimeMillis() - t0, 0, 0));
                continue;
            }
            long elapsed = System.currentTimeMillis() - t0;

            AiAssistResult fakeResult = new AiAssistResult(prices, description, "high", null);
            List<PriceItem> emptyItems = List.of();
            int violations = qualityRule.check(fakeResult, descCommand, emptyItems).size()
                    + hookRule.check(fakeResult, descCommand, emptyItems).size()
                    + personaRule.check(fakeResult, descCommand, emptyItems).size()
                    + reformatRule.check(fakeResult, descCommand, emptyItems).size();

            rows.add(Row.success(c.id(), elapsed, description.length(), violations));

            outputsFull.append("\n=== ").append(c.id()).append(" ===\n");
            outputsFull.append("memo: ").append(c.memo().replace("\n", " / ")).append('\n');
            outputsFull.append("analysis: ").append(analysis.productName()).append(" / ")
                    .append(analysis.grade()).append("급 / ").append(analysis.gradeReason()).append('\n');
            outputsFull.append("prices: low=").append(prices.low())
                    .append(" mid=").append(prices.mid())
                    .append(" high=").append(prices.high()).append('\n');
            outputsFull.append("latency_ms=").append(elapsed)
                    .append(" len=").append(description.length())
                    .append(" violations=").append(violations).append('\n');
            outputsFull.append("---\n").append(description).append("\n---\n");
        }

        Path outPath = Path.of("build", "gemini-description-live.txt");
        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, outputsFull.toString(), StandardCharsets.UTF_8);

        StringBuilder summary = new StringBuilder("\n=== GeminiDescriptionAdapter Live (10 cases) ===\n");
        summary.append("model=").append(props.getModel()).append('\n');
        int successes = 0;
        long totalElapsed = 0;
        int totalLen = 0;
        int casesWithViolation = 0;
        for (Row r : rows) {
            summary.append(String.format(Locale.ROOT,
                    "[%-30s] %s latency=%dms len=%d violations=%d%n",
                    r.id, r.error == null ? "OK  " : "FAIL", r.elapsedMs, r.length, r.violations));
            if (r.error != null) {
                summary.append("  error=").append(r.error).append('\n');
                continue;
            }
            successes++;
            totalElapsed += r.elapsedMs;
            totalLen += r.length;
            if (r.violations > 0) {
                casesWithViolation++;
            }
        }
        summary.append('\n');
        summary.append(String.format(Locale.ROOT,
                "success=%d/%d avg_latency=%dms avg_len=%d violations_cases=%d (%.1f%%)%n",
                successes, rows.size(),
                successes == 0 ? 0 : totalElapsed / successes,
                successes == 0 ? 0 : totalLen / successes,
                casesWithViolation,
                successes == 0 ? 0.0 : 100.0 * casesWithViolation / successes));

        System.out.println(summary);

        assertThat(successes).as("최소 성공률 70%").isGreaterThanOrEqualTo((int) Math.ceil(rows.size() * 0.7));
    }

    private record Row(String id, long elapsedMs, int length, int violations, String error) {
        static Row success(String id, long elapsed, int length, int violations) {
            return new Row(id, elapsed, length, violations, null);
        }

        static Row failure(String id, String error, long elapsed, int length, int violations) {
            return new Row(id, elapsed, length, violations, error);
        }
    }
}
