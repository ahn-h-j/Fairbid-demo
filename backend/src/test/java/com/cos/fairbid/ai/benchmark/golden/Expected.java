package com.cos.fairbid.ai.benchmark.golden;

/**
 * Golden 케이스의 기대 가격 범위.
 *
 * <ul>
 *   <li>{@code low} / {@code high} — 정답 가격 범위(원). 둘 다 필수.</li>
 *   <li>{@code tolerancePct} — 스코어링 로직에서 현재 사용되지 않는다. JSONL 스키마
 *       호환을 위해 필드만 보존(과거 Soft PASS 유산, Score100으로 교체 후 unused).</li>
 *   <li>{@code source} — 시세 출처 메모(당근 YYYY-MM 등). 평가엔 영향 없음, 감사 추적용.</li>
 * </ul>
 *
 * {@code mid}는 파생값으로 {@link #mid()}를 통해 계산한다.
 */
public record Expected(
        long low,
        long high,
        Integer tolerancePct,
        String source
) {
    /** tolerance_pct 누락 시 기본값. 스펙상 10%. */
    private static final int DEFAULT_TOLERANCE_PCT = 10;

    public int tolerancePctOrDefault() {
        return tolerancePct == null ? DEFAULT_TOLERANCE_PCT : tolerancePct;
    }

    /** 기대 중앙값. 스코어러의 strict/soft PASS 기준이 되는 중심점. */
    public long mid() {
        return (low + high) / 2;
    }
}
