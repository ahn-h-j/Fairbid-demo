package com.cos.fairbid.ai.benchmark;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.cos.fairbid.ai.adapter.out.guardrail.rules.ConfidenceTrackingRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionLengthRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionQualityRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.HookRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PersonaRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PriceStructureRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PromptInjectionRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.ReformatRule;
import com.cos.fairbid.ai.adapter.out.naver.NaverSearchProperties;
import com.cos.fairbid.ai.adapter.out.naver.NaverShoppingAdapter;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.application.port.out.GuardrailFailurePort;
import com.cos.fairbid.ai.application.port.out.PriceCachePort;
import com.cos.fairbid.ai.application.port.out.PriceSearchPort;
import com.cos.fairbid.ai.application.service.AiAssistService;
import com.cos.fairbid.ai.application.service.guardrail.InputGuardrailChain;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailChain;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.golden.GoldenCaseLoader;
import com.cos.fairbid.ai.benchmark.report.Reporter;
import com.cos.fairbid.ai.benchmark.runner.BenchmarkOrchestrator;
import com.cos.fairbid.ai.benchmark.runner.DryRunModelExecutor;
import com.cos.fairbid.ai.benchmark.runner.ModelAdapterFactory;
import com.cos.fairbid.ai.benchmark.runner.ModelExecutor;
import com.cos.fairbid.ai.benchmark.runner.NoOpPriceCachePort;
import com.cos.fairbid.ai.benchmark.runner.PipelineRateLimiter;
import com.cos.fairbid.ai.benchmark.runner.RealModelExecutor;

/**
 * AI 벤치마크 러너 — JUnit 엔트리 포인트.
 *
 * <p>{@code BENCHMARK_MODELS} 환경변수가 있을 때만 동작한다(평소 빌드에서 스킵).
 * 그 외 환경변수는 {@link BenchmarkSettings} 참고.</p>
 *
 * <h3>실행 예</h3>
 * <pre>
 *   BENCHMARK_MODELS=claude,gemini-2.5-pro \
 *   BENCHMARK_RUNS_PER_CASE=5 \
 *   BENCHMARK_CACHE_DISABLED=true \
 *   ANTHROPIC_API_KEY=sk-ant-... \
 *   GEMINI_API_KEY=... \
 *   NAVER_CLIENT_ID=... NAVER_CLIENT_SECRET=... \
 *   ./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
 * </pre>
 *
 * <h3>드라이런</h3>
 * <p>{@code BENCHMARK_DRY_RUN=true}면 실제 API 호출 없이 mock 응답으로 러너 파이프라인만
 * 검증한다 — API 키/네이버 크레덴셜 불필요.</p>
 *
 * <p>Spring 컨텍스트를 띄우지 않고 수동 와이어링을 사용한다(기존
 * {@code AiBaselineRunnerTest}와 동일 패턴). 이유:</p>
 * <ul>
 *   <li>여러 모델 어댑터를 동시에 인스턴스화해야 함({@code @ConditionalOnProperty}
 *       단일-provider 제약 우회).</li>
 *   <li>{@code FakeAiClient} @Primary 빈이 테스트 클래스패스에 있어 실제 어댑터가 묻힘.</li>
 *   <li>벤치마크 전용 설정(NoOp 캐시)을 명시적으로 고립.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_MODELS", matches = ".+")
class AiBenchmarkRunnerTest {

    @Test
    void runBenchmark() throws Exception {
        BenchmarkSettings settings = BenchmarkSettings.fromEnv();
        List<GoldenCase> cases = GoldenCaseLoader.loadFromClasspath(settings.casesClasspath());

        if (cases.isEmpty()) {
            throw new IllegalStateException(
                    "Golden dataset is empty: " + settings.casesClasspath());
        }

        // BENCHMARK_CASES_LIMIT — 스모크/디버깅용 앞 N건 자르기
        if (settings.casesLimit() != null) {
            int limit = Math.min(settings.casesLimit(), cases.size());
            cases = cases.subList(0, limit);
        }

        System.out.printf("[benchmark] loaded %d cases, models=%s, runsPerCase=%d, dryRun=%s, output=%s%n",
                cases.size(),
                settings.models(),
                settings.runsPerCase(),
                settings.dryRun(),
                settings.outputDir());

        Map<String, ModelExecutor> executors = settings.dryRun()
                ? buildDryRunExecutors(settings.models())
                : buildRealExecutors(settings.models());

        new BenchmarkOrchestrator(settings, cases, executors).run();

        // 실행 완료 후 모델별 report.md + 전체 comparison.md 생성
        Reporter.writeAllReports(settings.outputDir(), settings.models(), cases);
        System.out.println("[benchmark] reports written to " + settings.outputDir());
    }

    private Map<String, ModelExecutor> buildDryRunExecutors(List<String> models) {
        Map<String, ModelExecutor> executors = new HashMap<>();
        for (String model : models) {
            executors.put(model, new DryRunModelExecutor(model));
        }
        return executors;
    }

    /**
     * 각 모델마다:
     * <ol>
     *   <li>{@link ModelAdapterFactory}로 {@link AiClientPort} 구성</li>
     *   <li>공유 {@link AiAssistService} 의존성(가드레일, NoOp 캐시, 네이버 검색, 실패 포트)
     *       주입하여 모델 전용 서비스 조립</li>
     *   <li>{@link RealModelExecutor}에 래핑</li>
     * </ol>
     */
    private Map<String, ModelExecutor> buildRealExecutors(List<String> models) {
        InputGuardrailChain inputChain = new InputGuardrailChain(
                List.of(new PromptInjectionRule()));

        OutputGuardrailChain outputChain = new OutputGuardrailChain(List.of(
                new PriceStructureRule(),
                new DescriptionLengthRule(),
                new DescriptionQualityRule(),
                new PersonaRule(),
                new HookRule(),
                new ReformatRule(),
                new ConfidenceTrackingRule()));

        // 러너에서는 DB 없이 stdout 기록만 — 가드레일 실패는 리포터가 JSONL로 별도 추적.
        GuardrailFailurePort failurePort =
                (violations, category, keyword, aiMidPrice, searchMedian, attemptCount) -> {
                    // 필요 시 stdout 경고만 — 별도 집계는 raw-results.jsonl에서 수행
                };

        PriceCachePort noOpCache = new NoOpPriceCachePort();
        PriceSearchPort priceSearch = buildNaverSearch();

        ObjectMapper mapper = new ObjectMapper();
        ModelAdapterFactory factory = new ModelAdapterFactory(mapper);

        // provider별 rate limiter — 같은 provider(예: claude-sonnet + haiku)는 한 상한 공유
        PipelineRateLimiter claudeLimiter = new PipelineRateLimiter(parseIntEnv("BENCHMARK_RPM_CLAUDE", 0));
        PipelineRateLimiter openaiLimiter = new PipelineRateLimiter(parseIntEnv("BENCHMARK_RPM_OPENAI", 0));
        PipelineRateLimiter geminiLimiter = new PipelineRateLimiter(parseIntEnv("BENCHMARK_RPM_GEMINI", 0));

        Map<String, ModelExecutor> executors = new HashMap<>();
        for (String model : models) {
            String normalized = model.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            ModelAdapterFactory.ModelAdapter ma = factory.build(normalized);
            AiAssistService service = new AiAssistService(
                    ma.adapter(),
                    priceSearch,
                    noOpCache,
                    inputChain,
                    outputChain,
                    failurePort);
            PipelineRateLimiter limiter = resolveLimiter(normalized, claudeLimiter, openaiLimiter, geminiLimiter);
            // key는 BENCHMARK_MODELS 원문(raw) — orchestrator가 executors.get(raw)로 조회.
            // 모델 라벨(ModelAdapter.modelLabel)은 RawResult에 실제 모델 ID로 기록된다.
            executors.put(normalized, new RealModelExecutor(ma.modelLabel(), service, limiter));
        }
        logRpmConfig();
        return executors;
    }

    /**
     * 모델 이름 프리픽스 → provider별 공용 limiter 매핑.
     *
     * <ul>
     *   <li>{@code BENCHMARK_RPM_CLAUDE} — Claude 최대 RPM (0/미설정 = 무제한)</li>
     *   <li>{@code BENCHMARK_RPM_OPENAI} — OpenAI</li>
     *   <li>{@code BENCHMARK_RPM_GEMINI} — Gemini</li>
     * </ul>
     */
    private static PipelineRateLimiter resolveLimiter(
            String modelName,
            PipelineRateLimiter claude,
            PipelineRateLimiter openai,
            PipelineRateLimiter gemini) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("claude") || lower.startsWith("anthropic")
                || lower.startsWith("sonnet") || lower.startsWith("haiku") || lower.startsWith("opus")) {
            return claude;
        }
        if (lower.startsWith("gpt") || lower.startsWith("openai") || lower.startsWith("o1")) {
            return openai;
        }
        if (lower.startsWith("gemini") || lower.startsWith("google")) {
            return gemini;
        }
        // 알 수 없는 프리픽스 — 무제한
        return new PipelineRateLimiter(0);
    }

    private static void logRpmConfig() {
        int c = parseIntEnv("BENCHMARK_RPM_CLAUDE", 0);
        int o = parseIntEnv("BENCHMARK_RPM_OPENAI", 0);
        int g = parseIntEnv("BENCHMARK_RPM_GEMINI", 0);
        System.out.printf("[benchmark] RPM caps — claude=%s, openai=%s, gemini=%s%n",
                c == 0 ? "∞" : c,
                o == 0 ? "∞" : o,
                g == 0 ? "∞" : g);
    }

    private static int parseIntEnv(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private PriceSearchPort buildNaverSearch() {
        NaverSearchProperties props = new NaverSearchProperties();
        props.setClientId(requireEnv("NAVER_CLIENT_ID"));
        props.setClientSecret(requireEnv("NAVER_CLIENT_SECRET"));
        return new NaverShoppingAdapter(props);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " env is required (real benchmark needs Naver search creds)");
        }
        return value;
    }

    /** {@link Locale#ROOT} 기반 lowercase 비교용 헬퍼. 현재는 사용 안 하지만 확장 여지 남김. */
    @SuppressWarnings("unused")
    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
