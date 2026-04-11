package com.cos.fairbid.cucumber.config;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.exception.AiGenerationFailedException;
import com.cos.fairbid.ai.domain.exception.AiServiceUnavailableException;
import com.cos.fairbid.ai.domain.exception.InvalidImageException;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 테스트용 AiClientPort fake.
 *
 * Cucumber 시나리오에서 모드를 바꿔가며 정상/실패 응답을 시뮬레이션한다.
 * test 클래스패스에 @Primary @Component 로 등록되어 운영용 ClaudeApiAdapter 빈을 대체한다.
 */
@Component
@Primary
public class FakeAiClient implements AiClientPort {

    public enum Mode {
        SUCCESS,
        SERVICE_UNAVAILABLE,
        GENERATION_FAILED,
        INVALID_IMAGE
    }

    private Mode mode = Mode.SUCCESS;

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void reset() {
        this.mode = Mode.SUCCESS;
    }

    @Override
    public ProductAnalysis analyzeProduct(AiAssistCommand command) {
        checkFailure();
        return new ProductAnalysis("테스트 상품", "B", "테스트용 등급", "테스트 상품");
    }

    @Override
    public AiAssistResult generatePricing(
            AiAssistCommand command,
            ProductAnalysis analysis,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    ) {
        checkFailure();
        return new AiAssistResult(
                new SuggestedPrices(450_000L, 520_000L, 600_000L),
                "테스트용 더미 상품 설명입니다. 이 상품은 매우 좋은 상태이며, 사용감이 거의 없습니다. 구매 후 박스에 보관하여 외관 상태가 매우 양호합니다. 빠른 거래를 원하시는 분께 추천드립니다. 자세한 상태는 사진을 참고해주세요."
        );
    }

    private void checkFailure() {
        switch (mode) {
            case SERVICE_UNAVAILABLE -> throw AiServiceUnavailableException.of();
            case GENERATION_FAILED -> throw AiGenerationFailedException.of();
            case INVALID_IMAGE -> throw InvalidImageException.of();
        }
    }
}
