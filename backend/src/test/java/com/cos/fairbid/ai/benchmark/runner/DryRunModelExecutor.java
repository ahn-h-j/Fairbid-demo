package com.cos.fairbid.ai.benchmark.runner;

import java.time.Instant;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.score.VerdictScorer;

/**
 * 실제 모델을 호출하지 않고 결정론적 mock 응답을 반환하는 executor.
 *
 * <p>{@code BENCHMARK_DRY_RUN=true} 일 때 사용. 러너 자체(병렬성, JSONL 쓰기,
 * 재개성, 진행 로그)를 실제 API 비용 없이 검증한다.</p>
 *
 * <p>Mock 전략: 케이스의 {@code expected} 중앙값 그대로 반환 → Strict PASS = 1.0,
 * IoU = 1.0이 일관되게 나온다. 예외 경로 테스트가 필요하면 별도 {@code caseId} 패턴으로
 * 분기 가능하도록 열려 있다.</p>
 */
public final class DryRunModelExecutor implements ModelExecutor {

    private final String model;

    public DryRunModelExecutor(String model) {
        this.model = model;
    }

    @Override
    public RawResult execute(GoldenCase goldenCase, int runIdx) {
        long start = System.nanoTime();
        long recLow = goldenCase.expected().low();
        long recHigh = goldenCase.expected().high();
        long recMid = goldenCase.expected().mid();

        double strict = VerdictScorer.strictPass(goldenCase, recMid);
        double score = VerdictScorer.score100(goldenCase, recMid);
        double iou = VerdictScorer.iou(goldenCase, recLow, recHigh);

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return new RawResult(
                model,
                goldenCase.id(),
                runIdx,
                Instant.now(),
                recLow, recMid, recHigh,
                "high",
                "dry-run mock",
                strict, score, iou,
                latencyMs,
                null, null);
    }
}
