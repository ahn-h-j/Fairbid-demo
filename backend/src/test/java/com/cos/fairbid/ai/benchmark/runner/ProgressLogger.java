package com.cos.fairbid.ai.benchmark.runner;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 진행 상황 stdout 로거. 포맷: {@code [done/total] model / caseId run=r/R ... VERDICT}.
 *
 * <p>스펙 예시: {@code [3/150] claude / iphone-15-pro run=1/5 ... PASS}</p>
 *
 * <p>스레드 세이프 — 내부 카운터는 AtomicInteger, 출력은 PrintStream에 위임한다
 * (println은 synchronized)라서 줄이 섞이지 않는다.</p>
 */
public final class ProgressLogger {

    private final String model;
    private final int total;
    private final AtomicInteger done;
    private final PrintStream out;

    public ProgressLogger(String model, int total, int startedCount) {
        this(model, total, startedCount, System.out);
    }

    /** 테스트 편의용 — out을 주입 가능하게 오버로드. */
    ProgressLogger(String model, int total, int startedCount, PrintStream out) {
        this.model = model;
        this.total = total;
        this.done = new AtomicInteger(startedCount);
        this.out = out;
    }

    /**
     * 한 건의 완료를 로그로 남긴다.
     *
     * @param result 방금 완료된 RawResult
     * @param runsPerCase 케이스당 총 반복 수(r/R 표시용)
     */
    public void logCompleted(RawResult result, int runsPerCase) {
        int current = done.incrementAndGet();
        out.printf("[%d/%d] %s / %s run=%d/%d ... %s%n",
                current,
                total,
                model,
                result.caseId(),
                result.runIdx(),
                runsPerCase,
                result.verdict());
    }

    /** 이미 완료되어 건너뛴 건을 로그로 남긴다(재개 시). */
    public void logSkipped(String caseId, int runIdx, int runsPerCase) {
        int current = done.incrementAndGet();
        out.printf("[%d/%d] %s / %s run=%d/%d ... SKIP%n",
                current, total, model, caseId, runIdx, runsPerCase);
    }
}
