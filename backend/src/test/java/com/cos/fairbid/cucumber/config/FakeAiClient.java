package com.cos.fairbid.cucumber.config;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.exception.AiGenerationFailedException;
import com.cos.fairbid.ai.domain.exception.AiServiceUnavailableException;
import com.cos.fairbid.ai.domain.exception.InvalidImageException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트용 AiClientPort fake.
 *
 * Cucumber 시나리오에서 모드를 바꿔가며 정상/실패 응답을 시뮬레이션한다.
 * test 클래스패스에 @Primary @Component 로 등록되어 운영용 ClaudeApiAdapter 빈을 대체한다.
 *
 * (CLAUDE.md §4 — Mock 은 외부 API 에만 허용. Claude API 는 외부 API 에 해당.)
 *
 * 시나리오 간 모드가 새지 않도록 AiAssistSteps.@Before 에서 reset() 을 호출한다.
 */
@Component
@Primary
public class FakeAiClient implements AiClientPort {

    /** 시나리오에서 미리 설정해둘 응답 모드 */
    public enum Mode {
        /** 정상 응답: 고정된 더미 가격 + 설명 반환 */
        SUCCESS,
        /** 503: Claude API 장애 시뮬레이션 */
        SERVICE_UNAVAILABLE,
        /** 502: 응답 파싱/스키마 실패 시뮬레이션 */
        GENERATION_FAILED,
        /** 400: 이미지 분석 실패 시뮬레이션 */
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
    public AiAssistResult generate(AiAssistCommand command) {
        return switch (mode) {
            case SERVICE_UNAVAILABLE -> throw AiServiceUnavailableException.of();
            case GENERATION_FAILED -> throw AiGenerationFailedException.of();
            case INVALID_IMAGE -> throw InvalidImageException.of();
            case SUCCESS -> new AiAssistResult(
                    new SuggestedPrices(450_000L, 520_000L, 600_000L),
                    "테스트용 더미 상품 설명"
            );
        };
    }
}
