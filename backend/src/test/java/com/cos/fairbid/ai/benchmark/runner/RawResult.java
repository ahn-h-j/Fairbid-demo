package com.cos.fairbid.ai.benchmark.runner;

import java.time.Instant;

/**
 * 단일 (model × case × runIdx) 실행의 원자적 기록.
 *
 * <p>JSONL 1줄 = 1개 RawResult. 성공/실패 모두 기록되며, 리포터 단계가 집계한다.</p>
 *
 * <ul>
 *   <li>성공 시: {@code recLow/recMid/recHigh}, {@code confidence}, 스코어 3종이 채워진다.</li>
 *   <li>예외 시: 위 필드는 {@code null}, {@code exceptionType/exceptionMessage}가 채워진다.</li>
 * </ul>
 *
 * <p>{@code verdict()}는 집계 편의 메서드이며 다음 규칙을 따른다:</p>
 * <ul>
 *   <li>예외 발생 → {@code EXCEPTION}</li>
 *   <li>strict == 1.0 → {@code PASS}</li>
 *   <li>그 외 → {@code FAIL}</li>
 * </ul>
 *
 * <p>{@code score100}은 0~100 연속 점수로 PASS/FAIL 이분과 별개 지표다(평균 집계용).</p>
 */
public record RawResult(
        String model,
        String caseId,
        int runIdx,
        Instant timestamp,
        Long recLow,
        Long recMid,
        Long recHigh,
        String confidence,
        String confidenceReason,
        Double strictPass,
        Double score100,
        Double iou,
        long latencyMs,
        String exceptionType,
        String exceptionMessage
) {
    public String verdict() {
        if (exceptionType != null) {
            return "EXCEPTION";
        }
        if (strictPass != null && strictPass == 1.0) {
            return "PASS";
        }
        return "FAIL";
    }
}
