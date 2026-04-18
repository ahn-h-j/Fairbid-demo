package com.cos.fairbid.ai.benchmark.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.runner.RawResult;

/**
 * raw-results.jsonl 읽기 + {@link ReportAggregator} 호출 + {@link MarkdownRenderer}로
 * 파일 쓰기까지 오케스트레이션하는 편의 파사드.
 *
 * <p>출력 구조:</p>
 * <pre>
 *   {benchmarkRoot}/
 *     {model}/raw-results.jsonl
 *     {model}/report.md
 *     comparison.md
 * </pre>
 */
public final class Reporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Reporter() {
    }

    /**
     * 모델 전체에 대해 report.md + 루트에 comparison.md 작성.
     *
     * @param benchmarkRoot {@code build/benchmark/{timestamp}/}
     * @param models 처리할 모델 리스트 (BENCHMARK_MODELS 순서 유지)
     * @param cases 동일 GoldenDataset — 카테고리/태그 메타 주입용
     */
    public static void writeAllReports(
            Path benchmarkRoot,
            List<String> models,
            List<GoldenCase> cases) {
        List<ModelReport.Summary> summaries = new ArrayList<>();
        for (String model : models) {
            Path rawFile = benchmarkRoot.resolve(model).resolve("raw-results.jsonl");
            if (!Files.exists(rawFile)) {
                continue;
            }
            List<RawResult> results = readJsonl(rawFile);
            ModelReport.Summary summary = ReportAggregator.aggregate(model, results, cases);
            summaries.add(summary);

            Path reportFile = benchmarkRoot.resolve(model).resolve("report.md");
            writeString(reportFile, MarkdownRenderer.renderModelReport(summary));
        }

        if (!summaries.isEmpty()) {
            Path comparisonFile = benchmarkRoot.resolve("comparison.md");
            writeString(comparisonFile, MarkdownRenderer.renderComparison(summaries));
        }
    }

    /** JSONL → List&lt;RawResult&gt;. 빈 줄은 무시, 파싱 실패한 줄은 건너뛴다. */
    public static List<RawResult> readJsonl(Path file) {
        try {
            List<RawResult> out = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    out.add(MAPPER.readValue(line, RawResult.class));
                } catch (IOException ignored) {
                    // 부분 기록된 줄 — skip
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeString(Path file, String content) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
