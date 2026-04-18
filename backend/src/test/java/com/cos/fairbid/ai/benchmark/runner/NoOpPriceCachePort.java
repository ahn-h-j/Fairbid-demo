package com.cos.fairbid.ai.benchmark.runner;

import java.util.Optional;

import com.cos.fairbid.ai.application.port.out.PriceCachePort;
import com.cos.fairbid.ai.domain.AiAssistResult;

/**
 * 벤치마크용 NoOp {@link PriceCachePort}.
 *
 * <p>항상 MISS를 반환하고 저장은 무시한다. Redis 없이 실행할 수 있게 하며,
 * 매 run마다 모델을 실제로 호출하여 변동성을 관찰할 수 있게 한다(캐시 HIT로 인한
 * 점수 왜곡 방지).</p>
 *
 * <p>Spring 프로파일 {@code benchmark}에서 @Primary 빈으로 교체할 수도 있으나,
 * 현재 러너는 수동 와이어링을 사용하므로 해당 구현체를 직접 인스턴스화한다.</p>
 */
public final class NoOpPriceCachePort implements PriceCachePort {

    @Override
    public Optional<AiAssistResult> find(String category, String productKey, String grade) {
        return Optional.empty();
    }

    @Override
    public void save(String category, String productKey, String grade, AiAssistResult result) {
        // no-op
    }
}
