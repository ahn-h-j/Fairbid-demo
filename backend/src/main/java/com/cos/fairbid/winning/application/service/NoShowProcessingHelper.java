package com.cos.fairbid.winning.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;

/**
 * 노쇼 처리 헬퍼
 * 개별 Winning 노쇼 처리를 별도 트랜잭션에서 실행
 *
 * REQUIRES_NEW 전파를 사용하여 각 노쇼 처리가 독립적인 트랜잭션에서 실행되도록 함
 * 이로써 하나의 노쇼 처리 실패가 다른 처리에 영향을 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowProcessingHelper {

    private final WinningRepositoryPort winningRepository;
    private final AuctionRepositoryPort auctionRepository;
    private final NoShowProcessor noShowProcessor;

    /**
     * 단일 Winning 노쇼 처리
     * 독립적인 트랜잭션에서 실행 (REQUIRES_NEW)
     *
     * @param winningId 처리 대상 Winning ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExpiredWinning(Long winningId) {
        // 새 트랜잭션에서 Winning 다시 조회
        Winning winning = winningRepository.findById(winningId)
                .orElseThrow(() -> {
                    log.warn("Winning을 찾을 수 없습니다: {}", winningId);
                    return new IllegalStateException("Winning을 찾을 수 없습니다: " + winningId);
                });

        Long auctionId = winning.getAuctionId();
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        if (winning.getRank() == 1) {
            // 1순위 노쇼 처리 (도메인 서비스에 위임)
            noShowProcessor.processFirstRankNoShow(winning, auction);
        } else {
            // 2순위 노쇼 (승계 후 미응답) → 유찰 처리 (도메인 서비스에 위임)
            noShowProcessor.processSecondRankExpired(winning, auction);
        }
    }
}
