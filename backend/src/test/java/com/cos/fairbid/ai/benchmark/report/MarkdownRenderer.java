package com.cos.fairbid.ai.benchmark.report;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@link ModelReport.Summary} → 마크다운 문자열 렌더러.
 *
 * <p>집계와 렌더링을 분리해 테스트와 재사용을 쉽게 한다. 출력은 GitHub Flavored Markdown.</p>
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {
    }

    /** 단일 모델 리포트. 파일명은 {@code report.md} 권장. */
    public static String renderModelReport(ModelReport.Summary s) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Benchmark Report — ").append(s.model()).append("\n\n");
        sb.append("- Cases: ").append(s.totalCases())
                .append(", Total runs: ").append(s.totalRuns())
                .append(", Exceptions: ").append(s.exceptions())
                .append("\n\n");

        sb.append("## Overall\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        sb.append(row("Strict PASS rate", pct(s.strictPassRate())
                + "  (95% CI " + pct(s.strictCI().lower()) + " – " + pct(s.strictCI().upper()) + ")"));
        sb.append(row("Mean Score", scoreOutOf100(s.meanScore100())));
        sb.append(row("Mean IoU", fixed(s.meanIou())));
        sb.append(row("pass@1", fixed(s.passAt1())));
        sb.append(row("pass@3", nullableFixed(s.passAt3())));
        sb.append(row("pass^3", nullableFixed(s.passPower3())));
        sb.append("\n");

        sb.append("## By Category\n\n");
        sb.append(bucketTable(s.byCategory()));

        sb.append("## By Tag\n\n");
        if (s.byTag().isEmpty()) {
            sb.append("_(no tags in this dataset)_\n\n");
        } else {
            sb.append(bucketTable(s.byTag()));
        }

        sb.append("## Bottom 3 Cases\n\n");
        if (s.bottom3().isEmpty()) {
            sb.append("_(no cases)_\n\n");
        } else {
            sb.append("| Case | Category | Runs | Strict | Score | IoU |\n");
            sb.append("|---|---|---:|---:|---:|---:|\n");
            for (ModelReport.CaseStats cs : s.bottom3()) {
                sb.append("| ").append(cs.caseId())
                        .append(" | ").append(cs.category())
                        .append(" | ").append(cs.runs())
                        .append(" | ").append(pct(cs.strictPassRate()))
                        .append(" | ").append(score(cs.meanScore100()))
                        .append(" | ").append(fixed(cs.meanIou()))
                        .append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## Exceptions\n\n");
        if (s.exceptionList().isEmpty()) {
            sb.append("_(none)_\n");
        } else {
            sb.append("| Case | Run | Type | Message |\n|---|---:|---|---|\n");
            for (ModelReport.ExceptionEntry e : s.exceptionList()) {
                sb.append("| ").append(e.caseId())
                        .append(" | ").append(e.runIdx())
                        .append(" | ").append(e.type())
                        .append(" | ").append(escapePipe(e.message()))
                        .append(" |\n");
            }
        }

        return sb.toString();
    }

    /** 여러 모델 결과를 한 장의 비교표로 렌더. 파일명은 {@code comparison.md} 권장. */
    public static String renderComparison(List<ModelReport.Summary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Benchmark — Model Comparison\n\n");
        sb.append("| Model | Cases | Runs | Strict | Score | IoU | pass@1 | pass@3 | pass^3 | Exceptions |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (ModelReport.Summary s : summaries) {
            sb.append("| ").append(s.model())
                    .append(" | ").append(s.totalCases())
                    .append(" | ").append(s.totalRuns())
                    .append(" | ").append(pct(s.strictPassRate()))
                    .append(" | ").append(score(s.meanScore100()))
                    .append(" | ").append(fixed(s.meanIou()))
                    .append(" | ").append(fixed(s.passAt1()))
                    .append(" | ").append(nullableFixed(s.passAt3()))
                    .append(" | ").append(nullableFixed(s.passPower3()))
                    .append(" | ").append(s.exceptions())
                    .append(" |\n");
        }
        sb.append("\n_95% Wilson CI per model:_\n\n");
        for (ModelReport.Summary s : summaries) {
            sb.append("- **").append(s.model()).append("**: ")
                    .append(pct(s.strictCI().lower()))
                    .append(" – ").append(pct(s.strictCI().upper()))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String bucketTable(Map<String, ModelReport.BucketStats> buckets) {
        if (buckets.isEmpty()) {
            return "_(empty)_\n\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| Bucket | Cases | Runs | Strict | Score | IoU |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        for (ModelReport.BucketStats b : buckets.values()) {
            sb.append("| ").append(b.label())
                    .append(" | ").append(b.cases())
                    .append(" | ").append(b.runs())
                    .append(" | ").append(pct(b.strictPassRate()))
                    .append(" | ").append(score(b.meanScore100()))
                    .append(" | ").append(fixed(b.meanIou()))
                    .append(" |\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String row(String metric, String value) {
        return "| " + metric + " | " + value + " |\n";
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100.0);
    }

    private static String fixed(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    /** 버킷/케이스 셀용 간단 표기 — "87.5". */
    private static String score(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Overall 섹션용 강조 표기 — "87.5 / 100". */
    private static String scoreOutOf100(double v) {
        return String.format(Locale.ROOT, "%.1f / 100", v);
    }

    private static String nullableFixed(Double v) {
        return v == null ? "—" : fixed(v);
    }

    private static String escapePipe(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
