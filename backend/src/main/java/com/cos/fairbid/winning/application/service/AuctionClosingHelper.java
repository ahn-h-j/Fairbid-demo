package com.cos.fairbid.winning.application.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.TopBidderInfo;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.winning.application.port.out.AuctionClosedEventPublisherPort;
import com.cos.fairbid.winning.domain.Winning;

/**
 * 경매 종료 헬퍼
 * 개별 경매 종료 처리를 별도 트랜잭션에서 실행
 *
 * REQUIRES_NEW 전파를 사용하여 각 경매 처리가 독립적인 트랜잭션에서 실행되도록 함
 * 이로써 하나의 경매 처리 실패가 다른 경매에 영향을 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingHelper {

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final AuctionClosingProcessor closingProcessor;
    private final AuctionClosedEventPublisherPort eventPublisher;

    /**
     * 단일 경매 종료 처리
     * 독립적인 트랜잭션에서 실행 (REQUIRES_NEW)
     *
     * @param auctionId 종료 대상 경매 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAuctionClosing(Long auctionId) {
        // 새 트랜잭션에서 경매 다시 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 이미 종료된 경매는 스킵 (중복 처리 방지)
        if (auction.getStatus() == AuctionStatus.ENDED || auction.getStatus() == AuctionStatus.FAILED) {
            log.debug("이미 종료된 경매 스킵 - auctionId: {}, status: {}", auctionId, auction.getStatus());
            auctionCachePort.removeFromClosingQueue(auctionId);
            return;
        }

        // 1. Redis에서 1순위 입찰자 정보 조회 (Single Source of Truth)
        Optional<TopBidderInfo> topBidderOpt = auctionCachePort.getTopBidderInfo(auctionId);

        // 2. 입찰자가 없으면 유찰 처리
        if (topBidderOpt.isEmpty() || !topBidderOpt.get().isValid()) {
            closingProcessor.processNoWinner(auction);
            Auction savedAuction = auctionRepository.save(auction);

            // Redis 캐시 전체 갱신 + 종료 대기 큐 제거
            syncRedisAfterClosing(savedAuction);

            eventPublisher.publishAuctionClosed(auctionId);
            log.info("경매 유찰 완료 - auctionId: {}", auctionId);
            return;
        }

        // 3. 1순위 낙찰자 결정 (Redis 기준)
        TopBidderInfo topBidder = topBidderOpt.get();

        // Redis의 최신 currentPrice를 RDB 도메인에 반영 (일반 입찰 시 RDB 미갱신 대응)
        // Lua 스크립트에서 currentPrice == topBidAmount 이므로 topBidAmount를 사용
        auction.updateCurrentPriceFromCache(topBidder.bidAmount());

        closingProcessor.processFirstRankWinner(auction, topBidder);

        // 4. 2순위 후보 저장 (있는 경우, 1순위의 90% 이상인 경우만)
        Optional<TopBidderInfo> secondBidderOpt = auctionCachePort.getSecondBidderInfo(auctionId);
        if (secondBidderOpt.isPresent() && secondBidderOpt.get().isValid()) {
            TopBidderInfo secondBidder = secondBidderOpt.get();
            double threshold = topBidder.bidAmount() * Winning.AUTO_TRANSFER_THRESHOLD;
            if (secondBidder.bidAmount() >= threshold) {
                closingProcessor.saveSecondRankCandidate(auction, secondBidder);
            } else {
                log.debug("2순위 후보 미달 (90% 미만) - auctionId: {}, 1순위: {}, 2순위: {}",
                        auctionId, topBidder.bidAmount(), secondBidder.bidAmount());
            }
        }

        // 5. 경매 저장
        Auction savedAuction = auctionRepository.save(auction);

        // 6. Redis 캐시 전체 갱신 + 종료 대기 큐 제거
        syncRedisAfterClosing(savedAuction);

        // 7. 종료 이벤트 발행
        eventPublisher.publishAuctionClosed(auctionId);

        log.info("경매 종료 완료 - auctionId: {}, winnerId: {}", auctionId, topBidder.bidderId());
    }

    /**
     * 경매 종료 후 Redis 상태를 동기화한다
     * - 전체 캐시를 갱신하여 winnerId 등 모든 필드가 업데이트되도록 함
     * - 종료 대기 큐(Sorted Set)에서 제거하여 중복 처리 방지
     *
     * @param auction 종료된 경매
     */
    private void syncRedisAfterClosing(Auction auction) {
        try {
            // 전체 캐시 갱신 (winnerId 포함)
            auctionCachePort.saveToCache(auction);
            auctionCachePort.removeFromClosingQueue(auction.getId());
        } catch (Exception e) {
            // Redis 동기화 실패는 로그만 남기고 진행 (RDB는 이미 업데이트됨)
            log.warn("Redis 동기화 실패 - auctionId: {}, error: {}", auction.getId(), e.getMessage());
        }
    }
}
