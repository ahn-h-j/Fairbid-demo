package com.cos.fairbid.ai.application.port.out;

import java.util.List;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * 설명 생성 전용 Port Out (SPEC §19 옵션 B).
 *
 * <p>phase2b 에 해당한다. 이미지 재분석 없이 {@link ProductAnalysis} 와 가격 결과({@link SuggestedPrices})
 * + 원본 memo 를 입력받아 마케팅 톤 상품 설명(Markdown)만 반환한다.</p>
 *
 * <p>phase2a({@link AiClientPort#generatePricing}) 와 병렬 실행 가능하며, 가격 결과는 선택적으로
 * 참조(설명 내 가격대 언급 등). 구현 시점에서는 {@code null} 허용하지 않으며 서비스에서 phase2a
 * 결과를 기다렸다 전달한다 — 후속 최적화로 완전 병렬화 가능.</p>
 */
public interface DescriptionGeneratorPort {

    /**
     * 설명 생성.
     *
     * @param command          원본 요청 (memo, category 참조. imageUrls 는 사용하지 않음)
     * @param analysis         phase1 상품 식별 결과
     * @param suggestedPrices  phase2a 가격 결과 (설명 내 가격대 언급 용)
     * @param retryViolations  직전 시도의 SOFT/HARD 위반 목록 (null 이면 첫 시도)
     * @return Markdown 설명 문자열
     */
    String generateDescription(
            AiAssistCommand command,
            ProductAnalysis analysis,
            SuggestedPrices suggestedPrices,
            List<GuardrailViolation> retryViolations
    );
}
