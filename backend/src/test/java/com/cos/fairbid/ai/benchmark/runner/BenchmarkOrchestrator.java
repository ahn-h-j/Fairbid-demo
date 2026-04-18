package com.cos.fairbid.ai.benchmark.runner;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cos.fairbid.ai.benchmark.BenchmarkSettings;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * 벤치마크 실행 오케스트레이터.
 *
 * <p>루프 구조:</p>
 * <ol>
 *   <li>모델 리스트를 순차 처리 (모델 간 병렬 없음).</li>
 *   <li>각 모델에 대해 {@code raw-results.jsonl} 생성/이어쓰기.</li>
 *   <li>이미 기록된 {@code (caseId, runIdx)}는 건너뜀(재개성).</li>
 *   <li>케이스 {@link BenchmarkSettings#CASE_PARALLELISM}개를 병렬 실행,
 *       같은 케이스 내 {@code runIdx}는 순차 실행(호출 간 상관관계를 배제하지 않음).</li>
 * </ol>
 *
 * <p>모델 executor는 {@link ModelExecutor} 구현체를 외부에서 주입. dry-run은
 * {@link DryRunModelExecutor}, 실제 호출은 Step 4에서 구성.</p>
 */
public final class BenchmarkOrchestrator {

    private final BenchmarkSettings settings;
    private final List<GoldenCase> cases;
    private final Map<String, ModelExecutor> executors;

    public BenchmarkOrchestrator(
            BenchmarkSettings settings,
            List<GoldenCase> cases,
            Map<String, ModelExecutor> executors) {
        this.settings = settings;
        this.cases = cases;
        this.executors = executors;
    }

    /**
     * 벤치마크 실행. 모든 모델이 끝날 때까지 블로킹.
     *
     * <p>모델 간 병렬 실행 — 각 모델이 자기 스레드에서 독립적으로 run.
     * Rate limiter는 provider별로 공유되어 있어 병렬 실행이 상한을 깨지 않는다.
     * 실패(예외)는 raw-results.jsonl에 EXCEPTION으로 기록되고, 다른 모델의
     * 실행은 계속된다 — 한 모델의 장애가 전체 벤치마크를 중단시키지 않도록.</p>
     *
     * @throws IllegalStateException 모델 리스트와 executor 매핑이 불일치할 때
     */
    public void run() throws InterruptedException, ExecutionException {
        List<String> models = settings.models();
        if (models.isEmpty()) {
            return;
        }
        // executor 매핑 누락 사전 검증 — thread pool 에서 예외 처리하기보다 앞에서 실패.
        for (String model : models) {
            if (!executors.containsKey(model)) {
                throw new IllegalStateException("No executor wired for model: " + model);
            }
        }

        ExecutorService modelPool = Executors.newFixedThreadPool(models.size());
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (String model : models) {
                ModelExecutor executor = executors.get(model);
                futures.add(modelPool.submit(() -> {
                    runModel(model, executor);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                // 예외 발생 시 catch 후 다른 모델은 계속 끝내게 하는 편이 나을 수도 있으나,
                // 현재 계약은 "전체 중단" — 향후 필요하면 완화.
                future.get();
            }
        } finally {
            modelPool.shutdown();
            if (!modelPool.awaitTermination(30, TimeUnit.SECONDS)) {
                modelPool.shutdownNow();
            }
        }
    }

    private void runModel(String model, ModelExecutor executor)
            throws InterruptedException, ExecutionException {
        Path rawFile = settings.outputDir().resolve(model).resolve("raw-results.jsonl");
        RawResultWriter writer = new RawResultWriter(rawFile);
        Set<String> alreadyDone = ExistingResultsIndex.scan(rawFile);

        int totalRuns = cases.size() * settings.runsPerCase();
        ProgressLogger logger = new ProgressLogger(model, totalRuns, 0);

        // 이미 완료된 건은 먼저 skip 로그를 흘려 전체 진행률을 맞춤.
        for (GoldenCase gc : cases) {
            for (int runIdx = 1; runIdx <= settings.runsPerCase(); runIdx++) {
                if (alreadyDone.contains(gc.id() + "#" + runIdx)) {
                    logger.logSkipped(gc.id(), runIdx, settings.runsPerCase());
                }
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(BenchmarkSettings.CASE_PARALLELISM);
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (GoldenCase gc : cases) {
                futures.add(pool.submit(
                        () -> runCase(gc, model, executor, writer, logger, alreadyDone)));
            }
            for (Future<?> future : futures) {
                // 예외 발생 시 전체 중단(다른 futures는 shutdownNow로 정리).
                future.get();
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    /** 한 케이스를 순차 실행(runIdx 1..N). */
    private void runCase(
            GoldenCase gc,
            String model,
            ModelExecutor executor,
            RawResultWriter writer,
            ProgressLogger logger,
            Set<String> alreadyDone) {
        for (int runIdx = 1; runIdx <= settings.runsPerCase(); runIdx++) {
            if (alreadyDone.contains(gc.id() + "#" + runIdx)) {
                continue;
            }
            RawResult result;
            try {
                result = executor.execute(gc, runIdx);
            } catch (RuntimeException e) {
                // 개별 run 실패는 EXCEPTION으로 기록하고 다음 run으로 진행.
                result = buildExceptionResult(model, gc, runIdx, e);
            }
            writer.append(result);
            logger.logCompleted(result, settings.runsPerCase());
        }
    }

    private static RawResult buildExceptionResult(
            String model, GoldenCase gc, int runIdx, Throwable t) {
        return new RawResult(
                model, gc.id(), runIdx,
                java.time.Instant.now(),
                null, null, null,
                null, null,
                null, null, null,
                0L,
                t.getClass().getSimpleName(),
                t.getMessage());
    }
}
