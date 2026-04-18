package com.cos.fairbid.ai.benchmark.runner;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * 특정 모델에 대해 단일 Golden 케이스를 1회 실행하는 함수형 인터페이스.
 *
 * <p>구현체는 모델 호출, 지연 측정, 예외 처리, 스코어링까지 수행한 뒤
 * {@link RawResult}로 패키징해 반환한다.</p>
 *
 * <p>스레드 세이프해야 한다 — 오케스트레이터가 동일 모델 executor로 3 케이스를
 * 병렬로 호출하기 때문이다.</p>
 */
@FunctionalInterface
public interface ModelExecutor {
    RawResult execute(GoldenCase goldenCase, int runIdx);
}
