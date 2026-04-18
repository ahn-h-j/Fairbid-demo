package com.cos.fairbid.ai.benchmark.score;

/**
 * Wilson score 95% 신뢰구간 계산기.
 *
 * <p>정규근사(Wald) 대비 소표본에서 훨씬 정확하며, 특히 p가 0이나 1에 가까울 때
 * 경계가 [0, 1]을 벗어나지 않는다는 장점이 있다. 벤치마크는 반복 수가 적으므로
 * Wilson 이 적합하다.</p>
 *
 * <p>공식 (z = 1.96, 95% 신뢰수준):</p>
 * <pre>
 *   center = (p̂ + z²/(2n)) / (1 + z²/n)
 *   spread = z * sqrt( p̂(1-p̂)/n + z²/(4n²) ) / (1 + z²/n)
 *   CI = [center - spread, center + spread]
 * </pre>
 *
 * <p>n = 0 인 경우는 정보 없음으로 간주해 스펙상 [0, 1]을 반환한다.</p>
 */
public final class WilsonCI {

    /** 95% 신뢰수준 z-값. */
    private static final double Z_95 = 1.96;

    private WilsonCI() {
        // 유틸리티 클래스: 인스턴스화 금지
    }

    /**
     * Wilson 신뢰구간 하한/상한 쌍.
     */
    public record Bounds(double lower, double upper) {}

    /**
     * @param c 통과 횟수 (c ≤ n)
     * @param n 총 시행 횟수
     * @return 95% Wilson 신뢰구간
     */
    public static Bounds compute(int c, int n) {
        if (n == 0) {
            return new Bounds(0.0, 1.0);
        }
        double p = (double) c / (double) n;
        double z2 = Z_95 * Z_95;
        double denom = 1.0 + z2 / n;
        double center = (p + z2 / (2.0 * n)) / denom;
        double spread = Z_95 * Math.sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * n)) / denom;
        // 수치 안정성: 부동소수점 오차로 [0, 1] 벗어나는 케이스 방어
        double lower = Math.max(0.0, center - spread);
        double upper = Math.min(1.0, center + spread);
        return new Bounds(lower, upper);
    }
}
