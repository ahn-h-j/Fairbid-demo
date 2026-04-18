package com.cos.fairbid.ai.benchmark;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.golden.GoldenCaseLoader;
import com.cos.fairbid.ai.benchmark.report.Reporter;

/**
 * 기존 raw-results.jsonl 을 재집계하여 report.md / comparison.md 를 다시 생성.
 *
 * <p>{@code BENCHMARK_REGEN_DIR} 에 출력 디렉토리 지정. 모델 리스트도 {@code BENCHMARK_MODELS} 필요.</p>
 */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_REGEN_DIR", matches = ".+")
class RegenerateReportsTest {

    @Test
    void regenerate() {
        String regenDir = System.getenv("BENCHMARK_REGEN_DIR");
        String modelsRaw = System.getenv("BENCHMARK_MODELS");
        if (modelsRaw == null || modelsRaw.isBlank()) {
            throw new IllegalStateException("BENCHMARK_MODELS env required");
        }
        List<String> models = List.of(modelsRaw.split(","));
        List<GoldenCase> cases = GoldenCaseLoader.loadFromClasspath("ai/golden/cases.jsonl");
        Reporter.writeAllReports(Path.of(regenDir), models, cases);
        System.out.println("[regen] reports written to " + regenDir);
    }
}
