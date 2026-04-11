package com.cos.fairbid.ai.application.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.in.GenerateAuctionAssistUseCase;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.application.port.out.GuardrailFailurePort;
import com.cos.fairbid.ai.application.port.out.PriceSearchPort;
import com.cos.fairbid.ai.application.service.guardrail.InputGuardrailChain;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailChain;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;
import com.cos.fairbid.ai.domain.guardrail.OutputValidation;

/**
 * AI 경매 어시스턴트 UseCase 구현.
 *
 * 2단계 호출:
 *   1. 1차 Claude: 이미지 + memo → 상품 식별 + 등급 + 검색 키워드
 *   2. 네이버 검색
 *   3. 2차 Claude: 검색 결과 + 등급 → 추천가 + 설명 (confidence: high/low)
 *      - 검색 결과가 부실하면 confidence=low + 학습 지식 기반 추정
 *   4. 출력 가드레일: HARD 위반 → 재시도 1회, SOFT 위반 → DB 기록
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService implements GenerateAuctionAssistUseCase {

    private static final int PRICE_SEARCH_LIMIT = 10;

    /** 출력 가드레일 재시도 최대 횟수. 비용 방어를 위해 하드코딩. */
    private static final int MAX_GUARDRAIL_ATTEMPTS = 2;

    private final AiClientPort aiClientPort;
    private final PriceSearchPort priceSearchPort;
    private final InputGuardrailChain inputGuardrailChain;
    private final OutputGuardrailChain outputGuardrailChain;
    private final GuardrailFailurePort guardrailFailurePort;

    @Override
    public AiAssistResult generate(AiAssistCommand command) {
        log.info("AI assist 요청 - category={}, memoLen={}, imageCount={}",
                command.category(),
                command.memo() == null ? 0 : command.memo().length(),
                command.imageUrls().size());

        // 0. 입력 가드레일 (프롬프트 인젝션 차단)
        inputGuardrailChain.validate(command);

        // 1. 1차 Claude: 상품 식별 + 등급 + 검색 키워드
        ProductAnalysis analysis = aiClientPort.analyzeProduct(command);
        log.info("1차 분석 완료 - product={}, grade={}, keyword={}",
                analysis.productName(), analysis.grade(), analysis.searchKeyword());

        // 2. 네이버 검색
        List<PriceItem> priceItems = searchPrices(analysis.searchKeyword());

        // 3. 2차 Claude + 출력 가드레일 루프
        AiAssistResult bestResult = null;
        List<GuardrailViolation> lastViolations = List.of();

        for (int attempt = 1; attempt <= MAX_GUARDRAIL_ATTEMPTS; attempt++) {
            AiAssistResult result;
            if (attempt == 1) {
                result = aiClientPort.generatePricing(command, analysis, priceItems, null);
            } else {
                log.info("출력 가드레일 재시도 - attempt={}, violations={}", attempt, lastViolations.size());
                result = aiClientPort.generatePricing(command, analysis, priceItems, lastViolations);
            }
            bestResult = result;

            OutputValidation validation = outputGuardrailChain.validate(result, command, priceItems);

            if (validation.passed()) {
                logResponse(result, attempt);
                return result;
            }

            if (!validation.hasHardViolation()) {
                // SOFT만 → 반환 + DB 기록
                recordFailure(validation.violations(), command, analysis, result, priceItems, attempt);
                logResponse(result, attempt);
                return result;
            }

            // HARD 위반 → 다음 루프에서 재시도
            lastViolations = validation.hardViolations();
        }

        // 재시도 소진
        log.warn("출력 가드레일 재시도 소진 - attempts={}", MAX_GUARDRAIL_ATTEMPTS);
        recordFailure(lastViolations, command, analysis, bestResult, priceItems, MAX_GUARDRAIL_ATTEMPTS);
        logResponse(bestResult, MAX_GUARDRAIL_ATTEMPTS);
        return bestResult;
    }

    private void logResponse(AiAssistResult result, int attempt) {
        log.info("AI assist 응답 - low={}, mid={}, high={}, confidence={}, attempt={}",
                result.suggestedPrices().low(),
                result.suggestedPrices().mid(),
                result.suggestedPrices().high(),
                result.confidence(),
                attempt);
    }

    private List<PriceItem> searchPrices(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        log.info("시세 검색 keyword={}", keyword);
        return priceSearchPort.search(keyword, PRICE_SEARCH_LIMIT);
    }

    private void recordFailure(
            List<GuardrailViolation> violations,
            AiAssistCommand command,
            ProductAnalysis analysis,
            AiAssistResult result,
            List<PriceItem> priceItems,
            int attemptCount
    ) {
        String category = command.category() != null ? command.category().name() : null;
        String keyword = analysis != null ? analysis.searchKeyword() : null;
        Long aiMidPrice = result != null && result.suggestedPrices() != null
                ? result.suggestedPrices().mid() : null;
        Long searchMedian = calculateSearchMedian(priceItems);

        try {
            guardrailFailurePort.save(violations, category, keyword, aiMidPrice, searchMedian, attemptCount);
        } catch (Exception e) {
            log.warn("가드레일 실패 기록 저장 실패 - error={}", e.getMessage());
        }
    }

    private Long calculateSearchMedian(List<PriceItem> priceItems) {
        if (priceItems == null || priceItems.isEmpty()) {
            return null;
        }
        List<Long> shopPrices = priceItems.stream()
                .filter(item -> item.description() == null)
                .map(PriceItem::lprice)
                .filter(p -> p > 0)
                .sorted()
                .toList();
        if (shopPrices.isEmpty()) {
            return null;
        }
        return shopPrices.get(shopPrices.size() / 2);
    }
}
