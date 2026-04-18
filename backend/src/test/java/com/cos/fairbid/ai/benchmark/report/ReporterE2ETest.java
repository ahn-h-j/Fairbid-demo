package com.cos.fairbid.ai.benchmark.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cos.fairbid.ai.benchmark.BenchmarkSettings;
import com.cos.fairbid.ai.benchmark.golden.Expected;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.runner.BenchmarkOrchestrator;
import com.cos.fairbid.ai.benchmark.runner.DryRunModelExecutor;
import com.cos.fairbid.ai.benchmark.runner.ModelExecutor;

/**
 * 전체 파이프라인 E2E 검증:
 * Orchestrator → JSONL append → Reporter → report.md + comparison.md.
 *
 * <p>드라이런 executor를 쓰므로 API 키 불필요.</p>
 */
@DisplayName("Reporter E2E (dry-run → JSONL → Markdown)")
class ReporterE2ETest {

    private static GoldenCase caseOf(String id, String category, List<String> tags) {
        return new GoldenCase(id, category, "memo-" + id, null,
                new Expected(80_000, 120_000, 10, "t"), tags);
    }

    @Test
    @DisplayName("파이프라인 전체 — report.md + comparison.md가 기대 경로에 생성된다")
    void endToEnd(@TempDir Path tmp) throws Exception {
        List<GoldenCase> cases = List.of(
                caseOf("a", "FASHION", List.of("running")),
                caseOf("b", "ELECTRONICS", List.of("mobile")));

        BenchmarkSettings settings = new BenchmarkSettings(
                List.of("m1", "m2"), 3, true, true, tmp, "unused", null);

        Map<String, ModelExecutor> executors = Map.of(
                "m1", new DryRunModelExecutor("m1"),
                "m2", new DryRunModelExecutor("m2"));

        new BenchmarkOrchestrator(settings, cases, executors).run();
        Reporter.writeAllReports(tmp, settings.models(), cases);

        Path m1Report = tmp.resolve("m1/report.md");
        Path m2Report = tmp.resolve("m2/report.md");
        Path comparison = tmp.resolve("comparison.md");

        assertThat(m1Report).exists();
        assertThat(m2Report).exists();
        assertThat(comparison).exists();

        String m1Md = Files.readString(m1Report);
        // Dry-run은 항상 strict PASS → 100% 기대
        assertThat(m1Md).contains("# AI Benchmark Report — m1");
        assertThat(m1Md).contains("Strict PASS rate");
        assertThat(m1Md).contains("FASHION");
        assertThat(m1Md).contains("ELECTRONICS");
        assertThat(m1Md).contains("running");
        assertThat(m1Md).contains("mobile");

        String compMd = Files.readString(comparison);
        assertThat(compMd).contains("m1");
        assertThat(compMd).contains("m2");
        assertThat(compMd).contains("Model Comparison");
    }
}
