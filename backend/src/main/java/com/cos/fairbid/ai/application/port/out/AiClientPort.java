package com.cos.fairbid.ai.application.port.out;

import java.util.List;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;

/**
 * AI 모델 클라이언트 Port Out.
 *
 * v2 2단계 호출:
 * - phase1: 이미지 + memo → 상품 식별 + 등급 + 검색 키워드
 * - phase2: 1차 결과 + 검색 결과 → 추천가 + 상품 설명 (confidence high/low)
 */
public interface AiClientPort {

    /**
     * 1차 호출: 이미지 분석 → 상품 식별 + 등급 판정 + 검색 키워드
     */
    ProductAnalysis analyzeProduct(AiAssistCommand command);

    /**
     * 2차 호출: 가격 산정 + 설명 생성.
     * 검색 결과가 부실하면 confidence=low 로 학습 지식 기반 추정가 반환.
     */
    AiAssistResult generatePricing(
            AiAssistCommand command,
            ProductAnalysis analysis,
            List<PriceItem> priceItems,
            List<GuardrailViolation> retryViolations
    );
}
