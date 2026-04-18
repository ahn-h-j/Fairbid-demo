package com.cos.fairbid.ai.benchmark.score;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * 단일 실행 결과에 대한 채점기.
 *
 * <p>세 가지 지표를 산출한다:</p>
 * <ul>
 *   <li><b>Strict PASS</b> — 모델 중앙값이 기대 범위 안에 완전히 포함(1.0/0.0).</li>
 *   <li><b>Score100</b> — 0~100 연속 점수. 범위 안이면 100, 벗어나면 거리에 비례해 선형 감점.
 *       허용 거리(2.5 × 범위 폭) 이상 벗어나면 0.</li>
 *   <li><b>IoU</b> — 모델 추천 범위와 기대 범위의 교집합/합집합 비율.</li>
 * </ul>
 *
 * <p>모든 메서드는 순수 함수(무상태)다. 정적 유틸로 제공하며 인스턴스화를 금지한다.</p>
 */
public final class VerdictScorer {

    /** Score100 — 범위 폭에 대한 허용 거리 배율(점수가 0에 도달하는 거리 = 배율 × 폭). */
    private static final double SCORE100_TOLERANCE_MULTIPLIER = 2.5;

    /** Score100 — 범위 폭이 0일 때 폴백 tolerance 비율(|low| 기준). 2.5 × 10% = 25%. */
    private static final double SCORE100_FALLBACK_TOLERANCE_RATIO = 0.25;

    private VerdictScorer() {
        // 유틸리티 클래스: 인스턴스화 금지
    }

    /**
     * 엄격 일치 여부.
     *
     * @param goldenCase 정답 케이스
     * @param mid 모델이 제시한 중앙값(원)
     * @return mid ∈ [expected.low, expected.high] 이면 1.0, 아니면 0.0
     */
    public static double strictPass(GoldenCase goldenCase, long mid) {
        long low = goldenCase.expected().low();
        long high = goldenCase.expected().high();
        return (mid >= low && mid <= high) ? 1.0 : 0.0;
    }

    /**
     * 0~100 직관 점수.
     *
     * <ul>
     *   <li>{@code mid <= 0} → 0 (무효 추천)</li>
     *   <li>{@code mid ∈ [low, high]} → 100</li>
     *   <li>그 외 → {@code 100 × (1 - d / tolerance)}, 0 이하로 클램프</li>
     * </ul>
     *
     * <p>{@code tolerance = 2.5 × (high - low)}. 폭이 0이면
     * {@code 0.25 × max(1, |low|)} 를 폴백 tolerance로 사용한다.</p>
     *
     * @param goldenCase 정답 케이스
     * @param mid 모델이 제시한 중앙값(원). 음수/0 허용(0점 반환).
     * @return 0.0 ≤ score ≤ 100.0
     */
    public static double score100(GoldenCase goldenCase, long mid) {
        if (mid <= 0) {
            return 0.0;
        }
        long low = goldenCase.expected().low();
        long high = goldenCase.expected().high();
        if (mid >= low && mid <= high) {
            return 100.0;
        }
        // 가장 가까운 경계까지의 거리 — 범위 밖에 있을 때만 계산 도달.
        long distance = (mid < low) ? (low - mid) : (mid - high);
        long width = high - low;
        double tolerance = (width > 0)
                ? SCORE100_TOLERANCE_MULTIPLIER * width
                : SCORE100_FALLBACK_TOLERANCE_RATIO * Math.max(1, Math.abs(low));
        double ratio = distance / tolerance;
        if (ratio >= 1.0) {
            return 0.0;
        }
        return 100.0 * (1.0 - ratio);
    }

    /**
     * 추천 범위와 기대 범위의 IoU(Intersection over Union).
     *
     * <p>두 구간이 닫힌 실수 구간이라 가정하고 길이 기반 IoU를 계산한다.
     * 교집합이 없으면 0.0. 합집합 길이가 0인 퇴화 케이스(양쪽 모두 단일 점이고
     * 동일한 위치)는 스펙상 1.0으로 정의한다.</p>
     *
     * @param goldenCase 정답 케이스
     * @param recLow 모델 추천 범위 하한
     * @param recHigh 모델 추천 범위 상한(recLow 이상이어야 함)
     * @return [0.0, 1.0] 범위의 IoU
     */
    public static double iou(GoldenCase goldenCase, long recLow, long recHigh) {
        long expLow = goldenCase.expected().low();
        long expHigh = goldenCase.expected().high();

        long intersectionLow = Math.max(expLow, recLow);
        long intersectionHigh = Math.min(expHigh, recHigh);
        long intersection = Math.max(0, intersectionHigh - intersectionLow);

        long unionLow = Math.min(expLow, recLow);
        long unionHigh = Math.max(expHigh, recHigh);
        long union = unionHigh - unionLow;

        if (union == 0) {
            // 두 구간 모두 길이 0이고 동일한 위치 → 스펙상 완전 일치로 간주
            return 1.0;
        }
        return (double) intersection / (double) union;
    }
}
