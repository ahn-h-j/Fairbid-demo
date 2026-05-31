package com.cos.fairbid.ai.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.in.AiUsageLimitUseCase;
import com.cos.fairbid.ai.application.port.out.AiUsageLimitPort;
import com.cos.fairbid.ai.domain.exception.AiRateLimitExceededException;

/**
 * AI 사용 한도 유스케이스 구현.
 *
 * 사용자별 일일 한도와 전역 일일 한도를 함께 적용한다.
 * 게스트 체험 계정은 매 세션 새 userId가 발급되므로 사용자별 한도만으로는 비용이 새어나간다.
 * 따라서 전역 한도로 서비스 전체 일일 호출량 상한을 둔다.
 *
 * 한도 값은 application.yml에서 조정 가능하다.
 * - ai.assist.user-daily-limit (기본 5)
 * - ai.assist.global-daily-limit (기본 100)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageLimitService implements AiUsageLimitUseCase {

    private final AiUsageLimitPort aiUsageLimitPort;

    /** 사용자 1명이 하루에 받을 수 있는 AI 추천 성공 횟수. 정상 출력 1회 성공 후 제한. */
    @Value("${ai.assist.user-daily-limit:1}")
    private long userDailyLimit;

    /** 서비스 전체가 하루에 처리하는 AI 추천 성공 횟수 상한 (전체 API 비용 방어). */
    @Value("${ai.assist.global-daily-limit:100}")
    private long globalDailyLimit;

    @Override
    public void ensureWithinLimit(Long userId) {
        // 전역 한도 먼저 검사 — 서비스 전체 비용 상한이 가장 우선이다.
        long globalCount = aiUsageLimitPort.getGlobalDailyCount();
        if (globalCount >= globalDailyLimit) {
            log.warn("AI 추천 전역 일일 한도 초과 - count={}, limit={}", globalCount, globalDailyLimit);
            throw AiRateLimitExceededException.forGlobal();
        }

        long userCount = aiUsageLimitPort.getUserDailyCount(userId);
        if (userCount >= userDailyLimit) {
            log.info("AI 추천 사용자 일일 한도 초과 - userId={}, count={}, limit={}",
                    userId, userCount, userDailyLimit);
            throw AiRateLimitExceededException.forUser();
        }
    }

    @Override
    public void recordSuccess(Long userId) {
        aiUsageLimitPort.increment(userId);
    }
}
