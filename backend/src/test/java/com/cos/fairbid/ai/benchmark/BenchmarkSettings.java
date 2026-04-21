package com.cos.fairbid.ai.benchmark;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 벤치마크 러너 실행 설정. 환경변수로 제어한다.
 *
 * <ul>
 *   <li>{@code BENCHMARK_MODELS} — 쉼표 구분 모델 리스트 (예: {@code claude,openai,gemini}). 필수.</li>
 *   <li>{@code BENCHMARK_RUNS_PER_CASE} — 케이스당 반복 수. 기본 5.</li>
 *   <li>{@code BENCHMARK_CACHE_DISABLED} — {@code true}면 Redis 시세 캐시 우회.</li>
 *   <li>{@code BENCHMARK_DRY_RUN} — {@code true}면 실제 API 호출 없이 mock 응답으로 러너만 검증.</li>
 *   <li>{@code BENCHMARK_OUTPUT_DIR} — (선택) 결과 저장 루트. 미지정 시 {@code docs/benchmark-results/runs/{yyyyMMdd-HHmmss}} (gitignored). 본벤치·재검토 등 영속화할 측정은 {@code docs/benchmark-results/raw/{label}} 로 직접 지정한다.</li>
 *   <li>{@code BENCHMARK_CASES_PATH} — (선택) Golden JSONL 클래스패스 경로. 기본 {@code ai/golden/cases.jsonl}.</li>
 *   <li>{@code BENCHMARK_CASES_LIMIT} — (선택) 첫 N 건만 실행. 스모크/디버깅용. 미지정 시 전체.</li>
 * </ul>
 *
 * <p>출력 경로 구조: {@code {outputDir}/{model}/raw-results.jsonl}</p>
 */
public record BenchmarkSettings(
        List<String> models,
        int runsPerCase,
        boolean cacheDisabled,
        boolean dryRun,
        Path outputDir,
        String casesClasspath,
        Integer casesLimit
) {
    /** 모델당 동시 실행 케이스 수(동일 모델 내 병렬성). */
    public static final int CASE_PARALLELISM = 3;

    /** 기본 케이스 반복 수. */
    public static final int DEFAULT_RUNS_PER_CASE = 5;

    /** 기본 Golden JSONL 경로. */
    public static final String DEFAULT_CASES_PATH = "ai/golden/cases.jsonl";

    /**
     * 환경변수에서 설정을 구성한다.
     *
     * @throws IllegalStateException {@code BENCHMARK_MODELS}가 비었을 때
     */
    public static BenchmarkSettings fromEnv() {
        String modelsRaw = System.getenv("BENCHMARK_MODELS");
        if (modelsRaw == null || modelsRaw.isBlank()) {
            throw new IllegalStateException(
                    "BENCHMARK_MODELS env not set (e.g. 'claude,openai,gemini')");
        }
        List<String> models = Arrays.stream(modelsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        int runsPerCase = parseInt(System.getenv("BENCHMARK_RUNS_PER_CASE"), DEFAULT_RUNS_PER_CASE);
        boolean cacheDisabled = parseBool(System.getenv("BENCHMARK_CACHE_DISABLED"));
        boolean dryRun = parseBool(System.getenv("BENCHMARK_DRY_RUN"));

        Path outputDir;
        String outputOverride = System.getenv("BENCHMARK_OUTPUT_DIR");
        if (outputOverride != null && !outputOverride.isBlank()) {
            // 동일 경로로 재실행하면 raw-results.jsonl을 append하여 자연스럽게 재개됨.
            outputDir = Path.of(outputOverride);
        } else {
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .format(LocalDateTime.now());
            // gradle 기본 cwd 가 backend/ 이므로 프로젝트 루트로 올라가서 docs/ 를 해석한다.
            // 이렇게 하지 않으면 결과가 backend/docs/... 아래로 떨어져 raw 아카이브 정책과 충돌한다.
            Path cwd = Path.of("").toAbsolutePath();
            Path projectRoot = cwd.getFileName() != null
                    && "backend".equals(cwd.getFileName().toString())
                    ? cwd.getParent()
                    : cwd;
            outputDir = projectRoot.resolve(Path.of("docs", "benchmark-results", "runs", stamp));
        }

        String casesPath = System.getenv("BENCHMARK_CASES_PATH");
        if (casesPath == null || casesPath.isBlank()) {
            casesPath = DEFAULT_CASES_PATH;
        }

        String limitRaw = System.getenv("BENCHMARK_CASES_LIMIT");
        Integer casesLimit = null;
        if (limitRaw != null && !limitRaw.isBlank()) {
            try {
                int parsed = Integer.parseInt(limitRaw.trim());
                if (parsed > 0) {
                    casesLimit = parsed;
                }
            } catch (NumberFormatException ignored) {
                // 잘못된 값은 무시하고 전체 실행
            }
        }

        return new BenchmarkSettings(
                models, runsPerCase, cacheDisabled, dryRun, outputDir, casesPath, casesLimit);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String raw) {
        return raw != null && "true".equalsIgnoreCase(raw.trim());
    }
}
