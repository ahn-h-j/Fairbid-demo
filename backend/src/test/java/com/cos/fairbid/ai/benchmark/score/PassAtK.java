package com.cos.fairbid.ai.benchmark.score;

/**
 * 반복 실행 집계 지표.
 *
 * <p>케이스 1개를 N회 돌린 결과(통과 수 c, 전체 n)로부터 여러 가지 신뢰도
 * 지표를 계산한다. LLM 평가 문헌의 관례를 따른다.</p>
 *
 * <ul>
 *   <li><b>pass@1</b> — 단순 통과율 c/n.</li>
 *   <li><b>pass@k</b> — n개 중 k개를 무작위 비복원 추출할 때
 *       적어도 1개가 통과일 확률. HumanEval식 공식:
 *       {@code 1 - C(n-c, k) / C(n, k)}.
 *       실패 수(n-c)가 k보다 작으면 반드시 통과가 포함되므로 1.0.</li>
 *   <li><b>pass^k</b> — k번 독립 시도에서 모두 통과할 확률의 경험적 추정치
 *       {@code (c/n)^k}. 안정성 평가에 사용한다.</li>
 * </ul>
 */
public final class PassAtK {

    private PassAtK() {
        // 유틸리티 클래스: 인스턴스화 금지
    }

    /**
     * pass@1 = c / n. n=0이면 0.0 반환(정보 없음 기본값).
     */
    public static double passAt1(int c, int n) {
        if (n == 0) {
            return 0.0;
        }
        return (double) c / (double) n;
    }

    /**
     * pass@k. k <= n 이어야 하며, k가 n을 초과하면 정의되지 않는다.
     *
     * @throws IllegalArgumentException k가 범위를 벗어나면
     */
    public static double passAtK(int c, int n, int k) {
        if (n == 0) {
            return 0.0;
        }
        if (k <= 0 || k > n) {
            throw new IllegalArgumentException(
                    "k must be in [1, n], but k=" + k + ", n=" + n);
        }
        if (c < 0 || c > n) {
            throw new IllegalArgumentException(
                    "c must be in [0, n], but c=" + c + ", n=" + n);
        }
        // 실패가 k개 미만이면 k개를 뽑을 때 반드시 통과 포함 → 확률 1
        if (n - c < k) {
            return 1.0;
        }
        // 1 - C(n-c, k) / C(n, k)
        return 1.0 - (double) binomial(n - c, k) / (double) binomial(n, k);
    }

    /**
     * pass^k = (c/n)^k. 동일 샘플이 k번 독립 시도에서 모두 통과할 확률 추정.
     */
    public static double passPowerK(int c, int n, int k) {
        if (n == 0) {
            return 0.0;
        }
        return Math.pow((double) c / (double) n, k);
    }

    /**
     * 이항계수 C(n, k).
     *
     * <p>n, k가 작다는 전제(벤치마크 반복 수는 보통 1~10회). 오버플로 방지를 위해
     * 곱셈 후 나눗셈을 번갈아 수행하며, k와 n-k 중 작은 쪽을 사용해 루프 횟수를
     * 줄인다.</p>
     */
    static long binomial(int n, int k) {
        if (k < 0 || k > n) {
            return 0L;
        }
        if (k == 0 || k == n) {
            return 1L;
        }
        int kk = Math.min(k, n - k);
        long result = 1L;
        for (int i = 0; i < kk; i++) {
            // (n - i) * result 는 n이 작으면 안전. 매 스텝 나눗셈으로 정수 유지.
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
}
