package com.cos.fairbid.ai.application.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.port.in.GenerateAuctionAssistUseCase;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.domain.AiAssistResult;

/**
 * AI 경매 어시스턴트 UseCase 구현.
 *
 * v1에서는 단순 위임만 수행한다 (외부 AI 모델 호출).
 * v2에서 Redis 시세 테이블 조회/저장 분기가 이 자리에 들어갈 예정이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService implements GenerateAuctionAssistUseCase {

    private final AiClientPort aiClientPort;

    @Override
    public AiAssistResult generate(AiAssistCommand command) {
        log.info("AI assist 요청 - category={}, memoLen={}, imageCount={}",
                command.category(),
                command.memo() == null ? 0 : command.memo().length(),
                command.imageUrls().size());

        AiAssistResult result = aiClientPort.generate(command);

        log.info("AI assist 응답 - low={}, mid={}, high={}",
                result.suggestedPrices().low(),
                result.suggestedPrices().mid(),
                result.suggestedPrices().high());

        return result;
    }
}
