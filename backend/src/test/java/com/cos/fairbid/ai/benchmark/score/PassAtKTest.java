package com.cos.fairbid.ai.benchmark.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PassAtK")
class PassAtKTest {

    @Test
    @DisplayName("passAt1 = c/n")
    void passAt1Basic() {
        assertThat(PassAtK.passAt1(3, 5)).isEqualTo(0.6);
        assertThat(PassAtK.passAt1(0, 5)).isEqualTo(0.0);
        assertThat(PassAtK.passAt1(5, 5)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("passAt1 — n=0 이면 0.0")
    void passAt1ZeroN() {
        assertThat(PassAtK.passAt1(0, 0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("passAtK — n=5, c=3, k=3 → 1 - C(2,3)/C(5,3) = 1 (실패가 k보다 적음)")
    void passAtKFailuresLessThanK() {
        // 실패 n-c=2 < k=3 → 반드시 통과 포함
        assertThat(PassAtK.passAtK(3, 5, 3)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("passAtK — n=5, c=2, k=3 → 1 - C(3,3)/C(5,3) = 1 - 1/10 = 0.9")
    void passAtKPartial() {
        assertThat(PassAtK.passAtK(2, 5, 3)).isCloseTo(0.9, within(1e-9));
    }

    @Test
    @DisplayName("passAtK — 모두 통과(c=n)는 1.0")
    void passAtKAllPass() {
        assertThat(PassAtK.passAtK(5, 5, 3)).isEqualTo(1.0);
        assertThat(PassAtK.passAtK(5, 5, 1)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("passAtK — 모두 실패(c=0)는 0.0")
    void passAtKAllFail() {
        // C(n, k) / C(n, k) = 1 → 1 - 1 = 0
        assertThat(PassAtK.passAtK(0, 5, 3)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("passAtK — k > n 이면 IllegalArgumentException")
    void passAtKInvalidK() {
        assertThatThrownBy(() -> PassAtK.passAtK(3, 5, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("passAtK — n=0 이면 0.0 (예외 아님)")
    void passAtKZeroN() {
        assertThat(PassAtK.passAtK(0, 0, 1)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("passPowerK — (c/n)^k")
    void passPowerKBasic() {
        // (3/5)^3 = 0.216
        assertThat(PassAtK.passPowerK(3, 5, 3)).isCloseTo(0.216, within(1e-9));
        // (5/5)^3 = 1
        assertThat(PassAtK.passPowerK(5, 5, 3)).isEqualTo(1.0);
        // (0/5)^3 = 0
        assertThat(PassAtK.passPowerK(0, 5, 3)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("binomial — 기본 케이스")
    void binomialBasic() {
        // C(5,3) = 10
        assertThat(PassAtK.binomial(5, 3)).isEqualTo(10L);
        // C(5,0) = 1
        assertThat(PassAtK.binomial(5, 0)).isEqualTo(1L);
        // C(5,5) = 1
        assertThat(PassAtK.binomial(5, 5)).isEqualTo(1L);
        // C(10,4) = 210
        assertThat(PassAtK.binomial(10, 4)).isEqualTo(210L);
        // k > n 이면 0
        assertThat(PassAtK.binomial(3, 5)).isEqualTo(0L);
    }
}
