package com.cos.fairbid.ai.benchmark.runner;

import java.time.Instant;
import java.util.List;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.service.AiAssistService;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.score.VerdictScorer;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.auction.domain.Category;

/**
 * 실제 {@link AiAssistService} 파이프라인을 호출해 한 번의 실행 결과를
 * {@link RawResult}로 변환하는 executor.
 *
 * <p>가드레일/재시도/네이버 검색은 모두 {@link AiAssistService}가 담당하므로
 * 이 클래스는 command 변환, 지연 측정, 스코어링, 예외 래핑만 수행한다.</p>
 *
 * <p>프로덕션 로직은 수정하지 않는다 — 벤치마크 모드의 유일한 차이는 외부에서
 * 주입하는 {@link NoOpPriceCachePort}뿐이다.</p>
 *
 * <p>{@link PipelineRateLimiter}를 주입하면 provider별 RPM 상한을 지킨다.
 * API 호출이 자연스럽게 느리면 sleep=0(낭비 없음).</p>
 */
public final class RealModelExecutor implements ModelExecutor {

    /** 레이트 제한 없음 싱글턴 — 기본 생성자/테스트용. */
    private static final PipelineRateLimiter NO_LIMIT = new PipelineRateLimiter(0);

    private final String modelLabel;
    private final AiAssistService service;
    private final PipelineRateLimiter rateLimiter;

    public RealModelExecutor(String modelLabel, AiAssistService service) {
        this(modelLabel, service, NO_LIMIT);
    }

    public RealModelExecutor(
            String modelLabel, AiAssistService service, PipelineRateLimiter rateLimiter) {
        this.modelLabel = modelLabel;
        this.service = service;
        this.rateLimiter = rateLimiter == null ? NO_LIMIT : rateLimiter;
    }

    @Override
    public RawResult execute(GoldenCase goldenCase, int runIdx) {
        // 호출 직전 슬롯 예약 — 자연 지연이 길면 대기 없음.
        try {
            rateLimiter.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return buildException(goldenCase, runIdx, ie, 0L);
        }

        long startNs = System.nanoTime();
        AiAssistCommand command = toCommand(goldenCase);

        try {
            AiAssistResult result = service.generate(command);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            return buildSuccess(goldenCase, runIdx, result, latencyMs);
        } catch (RuntimeException e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            return buildException(goldenCase, runIdx, e, latencyMs);
        }
    }

    /**
     * GoldenCase → AiAssistCommand 변환.
     *
     * <p>{@code image_url}은 기본적으로 그대로 전달되지만, 상대 경로/로컬 파일은 API 서버가
     * HTTPS만 허용하므로 실패한다. 벤치마크 용도로 이미지를 스킵하려면
     * {@code BENCHMARK_SKIP_IMAGES=true} 환경변수. 이 경우 memo 단독 추론.</p>
     */
    private AiAssistCommand toCommand(GoldenCase goldenCase) {
        Category category = goldenCase.category() == null
                ? null
                : Category.valueOf(goldenCase.category());
        boolean skipImages = "true".equalsIgnoreCase(System.getenv("BENCHMARK_SKIP_IMAGES"));
        List<String> imageUrls = (!skipImages && goldenCase.imageUrl() != null)
                ? List.of(goldenCase.imageUrl())
                : List.of();
        return new AiAssistCommand(category, goldenCase.memo(), imageUrls);
    }

    private RawResult buildSuccess(
            GoldenCase goldenCase, int runIdx, AiAssistResult result, long latencyMs) {
        long recLow = result.suggestedPrices().low();
        long recMid = result.suggestedPrices().mid();
        long recHigh = result.suggestedPrices().high();

        double strict = VerdictScorer.strictPass(goldenCase, recMid);
        double score = VerdictScorer.score100(goldenCase, recMid);
        double iou = VerdictScorer.iou(goldenCase, recLow, recHigh);

        return new RawResult(
                modelLabel,
                goldenCase.id(),
                runIdx,
                Instant.now(),
                recLow, recMid, recHigh,
                result.confidence(),
                result.confidenceReason(),
                strict, score, iou,
                latencyMs,
                null, null);
    }

    private RawResult buildException(
            GoldenCase goldenCase, int runIdx, Throwable t, long latencyMs) {
        return new RawResult(
                modelLabel,
                goldenCase.id(),
                runIdx,
                Instant.now(),
                null, null, null,
                null, null,
                null, null, null,
                latencyMs,
                t.getClass().getSimpleName(),
                t.getMessage());
    }
}
