package com.cos.fairbid.ai.benchmark.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cos.fairbid.ai.benchmark.golden.Expected;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.runner.RawResult;

@DisplayName("ReportAggregator")
class ReportAggregatorTest {

    private static GoldenCase goldenCase(String id, String category, List<String> tags) {
        return new GoldenCase(id, category, "memo", null,
                new Expected(80_000, 120_000, 10, "test"), tags);
    }

    /** Strict PASS + score 100 (범위 한가운데 추천). */
    private static RawResult pass(String model, String caseId, int runIdx) {
        return new RawResult(model, caseId, runIdx, Instant.now(),
                80_000L, 100_000L, 120_000L, "high", "ok",
                1.0, 100.0, 1.0, 100L, null, null);
    }

    /** 범위 밖 FAIL — 범위 [80000,120000]에서 50000 이하. 스펙 곡선 기준 score=0~낮음. */
    private static RawResult fail(String model, String caseId, int runIdx) {
        // width=40000, tolerance=100000. mid=30000 → d=50000 → score=50
        return new RawResult(model, caseId, runIdx, Instant.now(),
                10_000L, 30_000L, 50_000L, "low", "miss",
                0.0, 50.0, 0.0, 120L, null, null);
    }

    /** 범위 근처 부분 실패 — score 중간대. */
    private static RawResult partial(String model, String caseId, int runIdx) {
        // mid=70000, d=10000 → score = 100*(1 - 10000/100000) = 90
        return new RawResult(model, caseId, runIdx, Instant.now(),
                60_000L, 70_000L, 90_000L, "low", "partial",
                0.0, 90.0, 0.25, 110L, null, null);
    }

    private static RawResult exception(String model, String caseId, int runIdx) {
        return new RawResult(model, caseId, runIdx, Instant.now(),
                null, null, null, null, null,
                null, null, null, 5000L,
                "TimeoutException", "timed out");
    }

    @Test
    @DisplayName("기본 집계 — strict/score/iou + pass@1 + Wilson CI")
    void basicAggregation() {
        List<GoldenCase> cases = List.of(
                goldenCase("a", "FASHION", List.of("running")),
                goldenCase("b", "ELECTRONICS", List.of()));

        // case a: 2 PASS, 1 FAIL (3 runs) — strict 2/3, score (100+100+50)/3 = 83.33
        // case b: 1 PASS, 1 PARTIAL (2 runs) — strict 1/2, score (100+90)/2 = 95
        List<RawResult> results = List.of(
                pass("m", "a", 1), pass("m", "a", 2), fail("m", "a", 3),
                pass("m", "b", 1), partial("m", "b", 2));

        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);

        assertThat(s.totalRuns()).isEqualTo(5);
        assertThat(s.totalCases()).isEqualTo(2);
        assertThat(s.strictPassRuns()).isEqualTo(3);
        assertThat(s.strictPassRate()).isCloseTo(0.6, within(1e-9));
        // meanScore = (100 + 100 + 50 + 100 + 90) / 5 = 88.0
        assertThat(s.meanScore100()).isCloseTo(88.0, within(1e-9));
        // IoU: Strict PASS run(3개, 각 iou=1.0)만 → 평균 1.0
        assertThat(s.meanIou()).isCloseTo(1.0, within(1e-9));
        // pass@1 매크로 평균: case a = 2/3, case b = 1/2 → (2/3 + 1/2)/2 = 7/12
        assertThat(s.passAt1()).isCloseTo(7.0 / 12.0, within(1e-9));
        assertThat(s.exceptions()).isEqualTo(0);
        assertThat(s.strictCI().lower()).isBetween(0.0, 0.6);
        assertThat(s.strictCI().upper()).isBetween(0.6, 1.0);
    }

    @Test
    @DisplayName("pass@3 / pass^3 — 모든 케이스가 3회 이상일 때만 계산")
    void passAtK() {
        List<GoldenCase> cases = List.of(goldenCase("a", "X", List.of()));
        List<RawResult> results = List.of(
                pass("m", "a", 1), pass("m", "a", 2), pass("m", "a", 3));
        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);
        assertThat(s.passAt3()).isEqualTo(1.0);
        assertThat(s.passPower3()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("pass@3 / pass^3 — run 수 < 3 이면 null")
    void passAtKNullWhenTooFewRuns() {
        List<GoldenCase> cases = List.of(goldenCase("a", "X", List.of()));
        List<RawResult> results = List.of(pass("m", "a", 1), pass("m", "a", 2));
        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);
        assertThat(s.passAt3()).isNull();
        assertThat(s.passPower3()).isNull();
    }

    @Test
    @DisplayName("예외 집계 — exception 필드 있는 run은 exceptionList에 포함되고 score 평균에선 제외")
    void exceptionsTracked() {
        List<GoldenCase> cases = List.of(goldenCase("a", "X", List.of()));
        List<RawResult> results = List.of(
                pass("m", "a", 1), exception("m", "a", 2), pass("m", "a", 3));
        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);
        assertThat(s.exceptions()).isEqualTo(1);
        assertThat(s.exceptionList()).hasSize(1);
        assertThat(s.exceptionList().get(0).type()).isEqualTo("TimeoutException");
        // score 평균은 예외 run 제외 → (100+100)/2 = 100
        assertThat(s.meanScore100()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    @DisplayName("카테고리/태그 버킷 — 케이스 분포에 맞춰 집계")
    void bucketAggregation() {
        List<GoldenCase> cases = List.of(
                goldenCase("a", "FASHION", List.of("running", "unisex")),
                goldenCase("b", "ELECTRONICS", List.of("unisex")));

        List<RawResult> results = List.of(
                pass("m", "a", 1), fail("m", "a", 2),
                pass("m", "b", 1), pass("m", "b", 2));

        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);

        assertThat(s.byCategory()).containsKeys("FASHION", "ELECTRONICS");
        assertThat(s.byCategory().get("FASHION").strictPassRate()).isCloseTo(0.5, within(1e-9));
        // FASHION bucket mean score: (100+50)/2 = 75
        assertThat(s.byCategory().get("FASHION").meanScore100()).isCloseTo(75.0, within(1e-9));
        assertThat(s.byCategory().get("ELECTRONICS").strictPassRate()).isCloseTo(1.0, within(1e-9));
        assertThat(s.byCategory().get("ELECTRONICS").meanScore100()).isCloseTo(100.0, within(1e-9));

        // unisex 태그: a(pass, fail) + b(pass, pass) = 3 strict of 4 runs
        assertThat(s.byTag().get("unisex").strictPassRate()).isCloseTo(0.75, within(1e-9));
        // running 태그: a만 (1/2 strict, score (100+50)/2=75)
        assertThat(s.byTag().get("running").strictPassRate()).isCloseTo(0.5, within(1e-9));
        assertThat(s.byTag().get("running").meanScore100()).isCloseTo(75.0, within(1e-9));
    }

    @Test
    @DisplayName("bottom3 — meanScore100 오름차순")
    void bottom3OrderedByScore() {
        List<GoldenCase> cases = List.of(
                goldenCase("best", "X", List.of()),
                goldenCase("mid", "X", List.of()),
                goldenCase("worst", "X", List.of()),
                goldenCase("extra", "X", List.of()));
        List<RawResult> results = List.of(
                pass("m", "best", 1), pass("m", "best", 2),     // score avg 100
                pass("m", "mid", 1), partial("m", "mid", 2),     // score avg (100+90)/2 = 95
                fail("m", "worst", 1), fail("m", "worst", 2),    // score avg 50
                pass("m", "extra", 1), pass("m", "extra", 2));   // score avg 100

        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);
        assertThat(s.bottom3()).hasSize(3);
        assertThat(s.bottom3().get(0).caseId()).isEqualTo("worst");  // score 50
        assertThat(s.bottom3().get(1).caseId()).isEqualTo("mid");    // score 95
        // best/extra 둘 다 100 — 동률인 상태로 하나 더 붙어 bottom3 채워짐
        assertThat(s.bottom3().get(2).meanScore100()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    @DisplayName("마크다운 렌더 — 주요 섹션이 모두 포함되고 Mean Score가 표시된다")
    void markdownContainsSections() {
        List<GoldenCase> cases = List.of(goldenCase("a", "FASHION", List.of("tagA")));
        List<RawResult> results = List.of(pass("m", "a", 1), fail("m", "a", 2));
        ModelReport.Summary s = ReportAggregator.aggregate("m", results, cases);

        String md = MarkdownRenderer.renderModelReport(s);
        assertThat(md).contains("# AI Benchmark Report — m");
        assertThat(md).contains("## Overall");
        assertThat(md).contains("Mean Score");
        assertThat(md).contains("/ 100");
        assertThat(md).contains("## By Category");
        assertThat(md).contains("## By Tag");
        assertThat(md).contains("## Bottom 3 Cases");
        assertThat(md).contains("## Exceptions");
        assertThat(md).contains("FASHION");
        assertThat(md).contains("tagA");
        // Soft 제거 확인
        assertThat(md).doesNotContain("Soft PASS");
        assertThat(md).doesNotContain("| Soft |");
    }

    @Test
    @DisplayName("비교 리포트 — 모델별 행 + Score 컬럼 포함")
    void comparisonRendering() {
        List<GoldenCase> cases = List.of(goldenCase("a", "X", List.of()));
        ModelReport.Summary s1 = ReportAggregator.aggregate(
                "claude", List.of(pass("claude", "a", 1)), cases);
        ModelReport.Summary s2 = ReportAggregator.aggregate(
                "gpt-5.1", List.of(fail("gpt-5.1", "a", 1)), cases);

        String md = MarkdownRenderer.renderComparison(List.of(s1, s2));
        assertThat(md).contains("claude");
        assertThat(md).contains("gpt-5.1");
        assertThat(md).contains("Model Comparison");
        assertThat(md).contains("95% Wilson CI");
        assertThat(md).contains("| Score |");
        assertThat(md).doesNotContain("| Soft |");
    }
}
