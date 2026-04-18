package com.cos.fairbid.ai.benchmark.runner;

/**
 * 파이프라인 호출 간 최소 간격을 보장하는 간단한 레이트 리미터.
 *
 * <p>스레드 여러 개가 동일 인스턴스를 공유하면, 다음 "슬롯" 시간을 원자적으로 예약해
 * 전체 RPM이 상한을 넘지 않도록 한다. 호출 순서:</p>
 *
 * <pre>
 *   acquire()  ── 슬롯 예약 + 필요한 만큼 sleep
 *   service.generate(...)  ── 실제 API 호출
 *   (다음 acquire 자연 대기)
 * </pre>
 *
 * <p>API 호출이 자연스럽게 느려 슬롯 간격보다 오래 걸리면 {@code acquire()}는 즉시 반환한다.
 * 호출이 빨라 슬롯 간격보다 짧을 때만 차이를 보충해 sleep한다. 즉 매 호출마다 고정 sleep하는
 * 단순 throttle과 달리, 자연 지연을 낭비하지 않는다.</p>
 *
 * <p>{@code maxPerMinute <= 0}이면 {@code acquire()}는 무동작(무제한).</p>
 */
public final class PipelineRateLimiter {

    private final long intervalMs;
    /** 다음에 실행해도 되는 시각(epoch ms). */
    private long nextSlotMs;

    public PipelineRateLimiter(int maxPerMinute) {
        this.intervalMs = (maxPerMinute <= 0) ? 0 : (60_000L / maxPerMinute);
        this.nextSlotMs = 0L;
    }

    /**
     * 다음 슬롯 시각까지 대기. 슬롯 예약과 대기 계산은 synchronized로 보호되지만,
     * 실제 sleep은 락 밖에서 수행해 다른 스레드의 acquire가 막히지 않게 한다.
     */
    public void acquire() throws InterruptedException {
        if (intervalMs == 0) {
            return;
        }
        long waitMs;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextSlotMs);
            nextSlotMs = slot + intervalMs;
            waitMs = slot - now;
        }
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
    }
}
