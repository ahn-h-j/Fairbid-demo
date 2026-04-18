package com.cos.fairbid.ai.benchmark.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cos.fairbid.ai.benchmark.BenchmarkSettings;
import com.cos.fairbid.ai.benchmark.golden.Expected;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * Orchestrator 통합 테스트 — dry-run 경로로 파이프라인(병렬 실행, JSONL append, 재개)을 검증.
 */
@DisplayName("BenchmarkOrchestrator")
class BenchmarkOrchestratorTest {

    private static GoldenCase caseOf(String id) {
        return new GoldenCase(
                id,
                "ELECTRONICS",
                "memo-" + id,
                null,
                new Expected(80_000, 120_000, 10, "test"),
                List.of("tag1"));
    }

    private BenchmarkSettings settingsFor(Path outputDir, int runsPerCase, List<String> models) {
        return new BenchmarkSettings(
                models, runsPerCase, true, true, outputDir, "unused", null);
    }

    @Test
    @DisplayName("dry-run 전체 파이프라인 — 모든 케이스×run이 JSONL에 기록된다")
    void endToEndDryRun(@TempDir Path tmp) throws Exception {
        List<GoldenCase> cases = List.of(caseOf("a"), caseOf("b"), caseOf("c"), caseOf("d"));
        int runsPerCase = 3;
        BenchmarkSettings settings = settingsFor(tmp, runsPerCase, List.of("m1", "m2"));

        Map<String, ModelExecutor> executors = Map.of(
                "m1", new DryRunModelExecutor("m1"),
                "m2", new DryRunModelExecutor("m2"));

        new BenchmarkOrchestrator(settings, cases, executors).run();

        // m1, m2 각각 raw-results.jsonl 생성
        Path m1 = tmp.resolve("m1/raw-results.jsonl");
        Path m2 = tmp.resolve("m2/raw-results.jsonl");
        assertThat(m1).exists();
        assertThat(m2).exists();

        // 4 cases × 3 runs = 12줄 (모델당)
        assertThat(Files.readAllLines(m1)).hasSize(12);
        assertThat(Files.readAllLines(m2)).hasSize(12);

        // 각 줄은 caseId 와 runIdx 를 담고 있다 (기록된 (caseId#runIdx) 집합이 기대와 일치)
        var keys = ExistingResultsIndex.scan(m1);
        assertThat(keys).containsExactlyInAnyOrder(
                "a#1", "a#2", "a#3",
                "b#1", "b#2", "b#3",
                "c#1", "c#2", "c#3",
                "d#1", "d#2", "d#3");
    }

    @Test
    @DisplayName("재개성 — 기존 JSONL에 기록된 run은 건너뛰고 나머지만 실행")
    void resumesFromExistingResults(@TempDir Path tmp) throws Exception {
        List<GoldenCase> cases = List.of(caseOf("x"), caseOf("y"));
        int runsPerCase = 2;
        BenchmarkSettings settings = settingsFor(tmp, runsPerCase, List.of("m1"));

        // 사전에 x#1 기록을 남겨 재개 케이스를 시뮬레이션
        Path rawFile = tmp.resolve("m1/raw-results.jsonl");
        Files.createDirectories(rawFile.getParent());
        String seed = """
                {"model":"m1","caseId":"x","runIdx":1,"timestamp":"%s","recLow":80000,"recMid":100000,"recHigh":120000,"confidence":"high","confidenceReason":"seed","strictPass":1.0,"score100":100.0,"iou":1.0,"latencyMs":0,"exceptionType":null,"exceptionMessage":null}
                """.formatted(Instant.now()).strip() + System.lineSeparator();
        Files.writeString(rawFile, seed);

        Map<String, ModelExecutor> executors = Map.of("m1", new DryRunModelExecutor("m1"));
        new BenchmarkOrchestrator(settings, cases, executors).run();

        // x#1(seed) + x#2, y#1, y#2 → 4줄
        assertThat(Files.readAllLines(rawFile)).hasSize(4);
        var keys = ExistingResultsIndex.scan(rawFile);
        assertThat(keys).containsExactlyInAnyOrder("x#1", "x#2", "y#1", "y#2");
    }

    @Test
    @DisplayName("executor 예외 시 EXCEPTION 기록 + 다음 run 계속")
    void exceptionsAreRecorded(@TempDir Path tmp) throws Exception {
        List<GoldenCase> cases = List.of(caseOf("bad"));
        int runsPerCase = 2;
        BenchmarkSettings settings = settingsFor(tmp, runsPerCase, List.of("boom"));

        ModelExecutor alwaysThrows = (gc, runIdx) -> {
            throw new IllegalStateException("boom-" + runIdx);
        };
        Map<String, ModelExecutor> executors = Map.of("boom", alwaysThrows);

        new BenchmarkOrchestrator(settings, cases, executors).run();

        Path rawFile = tmp.resolve("boom/raw-results.jsonl");
        List<String> lines = Files.readAllLines(rawFile);
        assertThat(lines).hasSize(2);
        // 두 줄 모두 exception 필드가 채워짐
        assertThat(lines).allMatch(l -> l.contains("\"exceptionType\":\"IllegalStateException\""));
        assertThat(lines).allMatch(l -> l.contains("\"exceptionMessage\":\"boom-"));
    }

    @Test
    @DisplayName("JSONL 로더 — 스펙 예시 포맷을 파싱한다 (snake_case)")
    void goldenCaseLoaderParsesSnakeCase() throws IOException {
        String jsonl = """
                {"id":"nike-af1","category":"FASHION","memo":"나이키\\n양호","image_url":"./x.jpg","expected":{"low":85000,"high":115000,"tolerance_pct":10,"source":"당근"},"tags":[]}
                {"id":"no-tags","category":"ELECTRONICS","memo":"m","image_url":null,"expected":{"low":100,"high":200}}
                """;
        var parsed = com.cos.fairbid.ai.benchmark.golden.GoldenCaseLoader.parse(
                new java.io.ByteArrayInputStream(jsonl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).id()).isEqualTo("nike-af1");
        assertThat(parsed.get(0).imageUrl()).isEqualTo("./x.jpg");
        assertThat(parsed.get(0).expected().tolerancePctOrDefault()).isEqualTo(10);
        assertThat(parsed.get(0).tags()).isEmpty();

        // tolerance_pct 누락 → 기본값 10
        assertThat(parsed.get(1).expected().tolerancePctOrDefault()).isEqualTo(10);
        // tags 누락 → 빈 리스트
        assertThat(parsed.get(1).tags()).isEmpty();
        assertThat(parsed.get(1).expected().mid()).isEqualTo(150);
    }
}
