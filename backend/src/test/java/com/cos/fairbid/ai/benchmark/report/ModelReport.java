package com.cos.fairbid.ai.benchmark.report;

import java.util.List;
import java.util.Map;

import com.cos.fairbid.ai.benchmark.score.WilsonCI;

/**
 * 단일 모델에 대한 집계 리포트 데이터.
 *
 * <p>모든 값은 {@link ReportAggregator}에서 계산되며, 마크다운 렌더링은
 * {@link MarkdownRenderer}가 담당한다(표현/집계 분리).</p>
 */
public final class ModelReport {

    /**
     * 모델 전체 요약.
     *
     * <p>{@code meanScore100}은 run 단위 Score100(0~100)의 평균이다. 예외 run은 null로
     * 제외된다.</p>
     */
    public record Summary(
            String model,
            int totalCases,
            int totalRuns,
            int strictPassRuns,
            int exceptions,
            double strictPassRate,
            double meanScore100,
            double meanIou,
            double passAt1,
            Double passAt3,
            Double passPower3,
            WilsonCI.Bounds strictCI,
            Map<String, BucketStats> byCategory,
            Map<String, BucketStats> byTag,
            List<CaseStats> bottom3,
            List<ExceptionEntry> exceptionList
    ) {}

    /** 카테고리/태그 단위 집계(버킷). */
    public record BucketStats(
            String label,
            int cases,
            int runs,
            double strictPassRate,
            double meanScore100,
            double meanIou
    ) {}

    /** 케이스 단위 집계 (bottom 3 산출용). */
    public record CaseStats(
            String caseId,
            String category,
            int runs,
            double strictPassRate,
            double meanScore100,
            double meanIou
    ) {}

    /** 예외 1건 요약. */
    public record ExceptionEntry(
            String caseId,
            int runIdx,
            String type,
            String message
    ) {}

    private ModelReport() {
    }
}
