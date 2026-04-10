package com.cos.fairbid.winning.application.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;

/**
 * 경매 종료 서비스
 *
 * 경매 종료 처리 흐름:
 * 1. Redis Sorted Set(auction:closing)에서 종료 대상 경매 ID 조회
 * 2. 각 경매를 별도 트랜잭션에서 종료 처리 (AuctionClosingHelper 위임)
 *
 * 기존 RDB 폴링 → Redis Sorted Set 조회로 변경하여
 * 입찰 연장/즉시구매로 변경된 종료 시간도 정확히 반영됨
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosingService implements CloseAuctionUseCase {

    private final AuctionCachePort auctionCachePort;
    private final AuctionClosingHelper auctionClosingHelper;

    @Override
    public void closeExpiredAuctions() {
        // 1. Redis Sorted Set에서 종료 시간이 지난 경매 ID 조회
        long nowMs = Instant.now().toEpochMilli();
        List<Long> auctionIds = auctionCachePort.findAuctionIdsToClose(nowMs);

        if (auctionIds.isEmpty()) {
            return;
        }

        log.info("종료 대상 경매 {}건 처리 시작", auctionIds.size());

        // 2. 각 경매 종료 처리 (별도 트랜잭션에서 실행)
        for (Long auctionId : auctionIds) {
            try {
                auctionClosingHelper.processAuctionClosing(auctionId);
            } catch (Exception e) {
                // 개별 경매 실패는 로그만 남기고 계속 진행
                log.error("경매 종료 처리 실패 - auctionId: {}, error: {}", auctionId, e.getMessage());
            }
        }

        log.info("종료 대상 경매 {}건 처리 완료", auctionIds.size());
    }
}
