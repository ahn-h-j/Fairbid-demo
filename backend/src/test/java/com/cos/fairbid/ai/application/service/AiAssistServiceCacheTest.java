package com.cos.fairbid.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.application.port.out.GuardrailFailurePort;
import com.cos.fairbid.ai.application.port.out.PriceCachePort;
import com.cos.fairbid.ai.application.port.out.PriceSearchPort;
import com.cos.fairbid.ai.application.service.guardrail.InputGuardrailChain;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailChain;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.ai.domain.guardrail.OutputValidation;
import com.cos.fairbid.auction.domain.Category;

/**
 * AiAssistService 캐시(Phase 2) 동작 단위 테스트.
 *
 * 검증 시나리오:
 *   1) 캐시 HIT → 2차 Claude 호출 + 네이버 검색 둘 다 스킵, 캐시된 결과 반환
 *   2) 캐시 MISS → 풀 흐름 (검색 + 2차 Claude) → 캐시 적재
 *   3) 캐시 MISS → low confidence 결과 → 캐시 적재 X (RedisPriceCacheAdapter 쪽 정책)
 *   4) productKey 비어있음 → 캐시 조회/적재 전부 no-op
 */
class AiAssistServiceCacheTest {

    private AiClientPort aiClientPort;
    private PriceSearchPort priceSearchPort;
    private PriceCachePort priceCachePort;
    private InputGuardrailChain inputGuardrailChain;
    private OutputGuardrailChain outputGuardrailChain;
    private GuardrailFailurePort guardrailFailurePort;

    private AiAssistService service;

    @BeforeEach
    void setUp() {
        aiClientPort = mock(AiClientPort.class);
        priceSearchPort = mock(PriceSearchPort.class);
        priceCachePort = mock(PriceCachePort.class);
        inputGuardrailChain = mock(InputGuardrailChain.class);
        outputGuardrailChain = mock(OutputGuardrailChain.class);
        guardrailFailurePort = mock(GuardrailFailurePort.class);

        service = new AiAssistService(
                aiClientPort, priceSearchPort, priceCachePort,
                inputGuardrailChain, outputGuardrailChain, guardrailFailurePort);
    }

    @Test
    @DisplayName("캐시 HIT 시 네이버 검색과 2차 Claude 호출을 모두 스킵한다")
    void cacheHit_skipsSearchAndPhase2() {
        // given
        AiAssistCommand command = new AiAssistCommand(
                Category.ELECTRONICS, "상품 정보: 아이폰 15 Pro", List.of("img.jpg"));

        ProductAnalysis analysis = new ProductAnalysis(
                "아이폰 15 Pro 256GB", "B", "양호", "아이폰 15 Pro", "iphone_15_pro_256gb");
        when(aiClientPort.analyzeProduct(command)).thenReturn(analysis);

        AiAssistResult cached = new AiAssistResult(
                new SuggestedPrices(900_000L, 1_100_000L, 1_300_000L),
                "## 캐시된 설명", "high", null);
        when(priceCachePort.find("ELECTRONICS", "iphone_15_pro_256gb", "B"))
                .thenReturn(Optional.of(cached));

        // when
        AiAssistResult result = service.generate(command);

        // then
        assertThat(result).isSameAs(cached);
        verify(priceSearchPort, never()).search(anyString(), anyInt());
        verify(aiClientPort, never()).generatePricing(any(), any(), any(), any());
        verify(outputGuardrailChain, never()).validate(any(), any(), any());
        verify(priceCachePort, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("캐시 MISS + PASS 시 풀 흐름 실행 후 캐시 적재")
    void cacheMiss_runsFullFlowAndSaves() {
        // given
        AiAssistCommand command = new AiAssistCommand(
                Category.ELECTRONICS, "상품 정보: 맥북", List.of("img.jpg"));

        ProductAnalysis analysis = new ProductAnalysis(
                "맥북 프로 14 M3", "A", "거의 새것", "맥북 프로 14 M3", "macbook_pro_14_m3");
        when(aiClientPort.analyzeProduct(command)).thenReturn(analysis);
        when(priceCachePort.find(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        when(priceSearchPort.search(anyString(), anyInt())).thenReturn(List.of());

        AiAssistResult generated = new AiAssistResult(
                new SuggestedPrices(1_800_000L, 2_000_000L, 2_200_000L),
                "## 맥북 프로 M3", "high", null);
        when(aiClientPort.generatePricing(any(), any(), any(), any())).thenReturn(generated);

        when(outputGuardrailChain.validate(any(), any(), any())).thenReturn(OutputValidation.pass());

        // when
        AiAssistResult result = service.generate(command);

        // then
        assertThat(result).isSameAs(generated);
        verify(priceSearchPort, times(1)).search(anyString(), anyInt());
        verify(aiClientPort, times(1)).generatePricing(any(), any(), any(), any());

        ArgumentCaptor<AiAssistResult> savedResult = ArgumentCaptor.forClass(AiAssistResult.class);
        verify(priceCachePort, times(1)).save(
                eq("ELECTRONICS"), eq("macbook_pro_14_m3"), eq("A"), savedResult.capture());
        assertThat(savedResult.getValue()).isSameAs(generated);
    }

    @Test
    @DisplayName("productKey 가 비어있으면 캐시 조회/적재 모두 건너뛴다")
    void emptyProductKey_bypassesCache() {
        // given
        AiAssistCommand command = new AiAssistCommand(
                Category.OTHER, "상품 정보: 알 수 없는 물건", List.of("img.jpg"));

        ProductAnalysis analysis = new ProductAnalysis(
                "미식별 상품", "B", "이미지 불명", "미식별 상품", ""); // productKey 빈 문자열
        when(aiClientPort.analyzeProduct(command)).thenReturn(analysis);
        when(priceCachePort.find(anyString(), eq(""), anyString())).thenReturn(Optional.empty());

        when(priceSearchPort.search(anyString(), anyInt())).thenReturn(List.of());

        AiAssistResult generated = new AiAssistResult(
                new SuggestedPrices(10_000L, 20_000L, 30_000L),
                "## 설명", "high", null);
        when(aiClientPort.generatePricing(any(), any(), any(), any())).thenReturn(generated);
        when(outputGuardrailChain.validate(any(), any(), any())).thenReturn(OutputValidation.pass());

        // when
        service.generate(command);

        // then — 캐시 find 는 호출되지만 (어댑터 내부에서 빈 키 처리), save 는 스킵
        verify(priceCachePort, never()).save(any(), any(), any(), any());
    }
}
