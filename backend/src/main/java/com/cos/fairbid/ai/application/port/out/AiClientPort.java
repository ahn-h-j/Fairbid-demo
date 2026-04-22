package com.cos.fairbid.ai.application.port.out;

import java.util.List;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.domain.PricingResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * AI 가격 클라이언트 Port Out (SPEC §19 옵션 B 적용).
 *
 * <p>2단계 호출:</p>
 * <ul>
 *   <li>phase1: 이미지 + memo → 상품 식별 + 등급 + 검색 키워드</li>
 *   <li>phase2a: 1차 결과 + 검색 결과 → 추천가 3구간 + confidence</li>
 * </ul>
 *
 * <p>설명 생성은 {@link DescriptionGeneratorPort}(phase2b) 로 분리되어 있다.</p>
 */
public interface AiClientPort {

    /**
     * phase1: 이미지 분석 → 상품 식별 + 등급 판정 + 검색 키워드
     */
    ProductAnalysis analyzeProduct(AiAssistCommand command);

    /**
     * phase2a: 가격 산정 (설명 없음).
     * 검색 결과가 부실하면 confidence=low 로 학습 지식 기반 추정가 반환.
     */
    PricingResult generatePricing(
            AiAssistCommand command,
            ProductAnalysis analysis,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    );
}
