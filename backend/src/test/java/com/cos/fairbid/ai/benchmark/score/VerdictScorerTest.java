package com.cos.fairbid.ai.benchmark.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.cos.fairbid.ai.benchmark.golden.Expected;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * VerdictScorer 단위 테스트 — 경계값, Score100 곡선, IoU 관계를 검증한다.
 */
@DisplayName("VerdictScorer")
class VerdictScorerTest {

    /** 공통 fixture. tolerancePct는 Score100에서 사용되지 않지만 JSONL 스키마 호환용으로 받는다. */
    private static GoldenCase caseOf(long low, long high, Integer tolerancePct) {
        return new GoldenCase(
                "test",
                "ELECTRONICS",
                "",
                null,
                new Expected(low, high, tolerancePct, null),
                List.of());
    }

    @Nested
    @DisplayName("strictPass")
    class StrictPass {
        @Test
        @DisplayName("범위 내부면 1.0")
        void insideRange() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.strictPass(gc, 100_000)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("하한 경계(low) 포함")
        void lowerBoundInclusive() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.strictPass(gc, 80_000)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("상한 경계(high) 포함")
        void upperBoundInclusive() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.strictPass(gc, 120_000)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("하한 바로 아래는 실패")
        void justBelowLower() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.strictPass(gc, 79_999)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("상한 바로 위는 실패")
        void justAboveUpper() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.strictPass(gc, 120_001)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("score100")
    class Score100 {
        /** 스펙 예시에 쓰인 nike: expected 80000~140000, width 60000, tolerance = 150000. */
        private final GoldenCase nike = caseOf(80_000, 140_000, null);

        @Test
        @DisplayName("범위 한가운데 → 100")
        void insideCenter() {
            assertThat(VerdictScorer.score100(nike, 100_000)).isEqualTo(100.0);
        }

        @Test
        @DisplayName("경계(low, high) → 100")
        void boundariesIn() {
            assertThat(VerdictScorer.score100(nike, 80_000)).isEqualTo(100.0);
            assertThat(VerdictScorer.score100(nike, 140_000)).isEqualTo(100.0);
        }

        @Test
        @DisplayName("75000 (5000 빗나감) → 거의 100")
        void slightMiss() {
            // d=5000, tolerance=150000 → 100*(1 - 5000/150000) ≈ 96.67
            double s = VerdictScorer.score100(nike, 75_000);
            assertThat(s).isCloseTo(96.666, within(0.01));
            assertThat(s).isGreaterThan(95.0);
        }

        @Test
        @DisplayName("50000 (30000 빗나감) → 80 근처")
        void moderateMiss() {
            // d=30000, tolerance=150000 → 100*(1 - 0.2) = 80.0
            assertThat(VerdictScorer.score100(nike, 50_000))
                    .isCloseTo(80.0, within(1e-6));
        }

        @Test
        @DisplayName("200000 (60000 빗나감 above) → 60 근처")
        void aboveMiss() {
            // d=60000, tolerance=150000 → 60.0
            assertThat(VerdictScorer.score100(nike, 200_000))
                    .isCloseTo(60.0, within(1e-6));
        }

        @Test
        @DisplayName("tolerance 바로 경계(+150000) → 0 (클램프 직전)")
        void atToleranceEdge() {
            // d=150000 = tolerance → ratio=1 → 0
            assertThat(VerdictScorer.score100(nike, 290_000)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("훨씬 벗어남 → 0")
        void farMiss() {
            assertThat(VerdictScorer.score100(nike, 1_000_000)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("mid == 0 → 0")
        void zeroMid() {
            assertThat(VerdictScorer.score100(nike, 0L)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("mid < 0 → 0")
        void negativeMid() {
            assertThat(VerdictScorer.score100(nike, -10_000L)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("width 0 폴백 — expected [100000, 100000]")
        void widthZeroFallback() {
            GoldenCase point = caseOf(100_000, 100_000, null);
            // tolerance = 0.25 * 100000 = 25000
            // mid=100000 exact → 100
            assertThat(VerdictScorer.score100(point, 100_000)).isEqualTo(100.0);
            // d=5000, tolerance=25000 → 100*(1 - 0.2) = 80
            assertThat(VerdictScorer.score100(point, 105_000))
                    .isCloseTo(80.0, within(1e-6));
            // d=25000 → 0
            assertThat(VerdictScorer.score100(point, 125_000)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("단조성 — 경계에서 멀어질수록 점수는 감소 또는 같음")
        void monotonicDecay() {
            double s0 = VerdictScorer.score100(nike, 100_000); // inside: 100
            double s1 = VerdictScorer.score100(nike, 70_000);
            double s2 = VerdictScorer.score100(nike, 50_000);
            double s3 = VerdictScorer.score100(nike, 30_000);
            double s4 = VerdictScorer.score100(nike, 10_000);
            assertThat(s0).isGreaterThanOrEqualTo(s1);
            assertThat(s1).isGreaterThanOrEqualTo(s2);
            assertThat(s2).isGreaterThanOrEqualTo(s3);
            assertThat(s3).isGreaterThanOrEqualTo(s4);
        }
    }

    @Nested
    @DisplayName("iou")
    class IoU {
        @Test
        @DisplayName("완전 일치 → 1.0")
        void perfectOverlap() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.iou(gc, 80_000, 120_000)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("부분 겹침 — [80,120] vs [100,140] → 1/3")
        void partialOverlap() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.iou(gc, 100_000, 140_000))
                    .isCloseTo(1.0 / 3.0, within(1e-9));
        }

        @Test
        @DisplayName("무겹침 → 0.0")
        void noOverlap() {
            GoldenCase gc = caseOf(80_000, 100_000, 10);
            assertThat(VerdictScorer.iou(gc, 120_000, 140_000)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("한쪽이 다른 쪽을 포함 → 작은쪽/큰쪽")
        void containment() {
            GoldenCase gc = caseOf(80_000, 120_000, 10);
            assertThat(VerdictScorer.iou(gc, 90_000, 110_000)).isEqualTo(0.5);
        }

        @Test
        @DisplayName("경계 접촉(touch)만 있을 때 → 0.0")
        void touchOnly() {
            GoldenCase gc = caseOf(80_000, 100_000, 10);
            assertThat(VerdictScorer.iou(gc, 100_000, 120_000)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("합집합 길이 0(양쪽 단일 점, 동일 위치) → 1.0")
        void degenerateEqualPoints() {
            GoldenCase gc = caseOf(100_000, 100_000, 10);
            assertThat(VerdictScorer.iou(gc, 100_000, 100_000)).isEqualTo(1.0);
        }
    }
}
