package com.cos.fairbid.ai.benchmark.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WilsonCI")
class WilsonCITest {

    /**
     * 참조값은 R의 {@code binom.test(8, 10)$conf.int} (Clopper-Pearson은 다르지만
     * Wilson은 아래 공식으로 직접 계산) 또는 Python statsmodels
     * {@code proportion_confint(8, 10, method='wilson')} 로 검증 가능.
     *
     * <p>수작업 계산 (z=1.96, n=10, c=8, p̂=0.8):
     * <ul>
     *   <li>z² = 3.8416</li>
     *   <li>denom = 1 + 0.38416 = 1.38416</li>
     *   <li>center = (0.8 + 0.19208) / 1.38416 ≈ 0.71668</li>
     *   <li>spread = 1.96 * sqrt(0.016 + 0.009604) / 1.38416 ≈ 0.22659</li>
     *   <li>CI ≈ [0.49009, 0.94327]</li>
     * </ul>
     */
    @Test
    @DisplayName("n=10, c=8 — 95% CI ≈ [0.490, 0.943]")
    void n10c8() {
        WilsonCI.Bounds b = WilsonCI.compute(8, 10);
        assertThat(b.lower()).isCloseTo(0.49009, within(1e-4));
        assertThat(b.upper()).isCloseTo(0.94327, within(1e-4));
    }

    @Test
    @DisplayName("n=0 이면 [0, 1] (정보 없음)")
    void zeroN() {
        WilsonCI.Bounds b = WilsonCI.compute(0, 0);
        assertThat(b.lower()).isEqualTo(0.0);
        assertThat(b.upper()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("모두 성공(c=n) — 상한은 1.0, 하한은 0 초과")
    void allSuccess() {
        WilsonCI.Bounds b = WilsonCI.compute(10, 10);
        assertThat(b.upper()).isEqualTo(1.0);
        assertThat(b.lower()).isGreaterThan(0.0).isLessThan(1.0);
    }

    @Test
    @DisplayName("모두 실패(c=0) — 하한은 0, 상한은 1 미만")
    void allFail() {
        WilsonCI.Bounds b = WilsonCI.compute(0, 10);
        assertThat(b.lower()).isEqualTo(0.0);
        assertThat(b.upper()).isGreaterThan(0.0).isLessThan(1.0);
    }

    @Test
    @DisplayName("하한 ≤ 상한 관계 유지")
    void monotonicity() {
        for (int n = 1; n <= 30; n++) {
            for (int c = 0; c <= n; c++) {
                WilsonCI.Bounds b = WilsonCI.compute(c, n);
                assertThat(b.lower()).isLessThanOrEqualTo(b.upper());
                assertThat(b.lower()).isBetween(0.0, 1.0);
                assertThat(b.upper()).isBetween(0.0, 1.0);
            }
        }
    }
}
