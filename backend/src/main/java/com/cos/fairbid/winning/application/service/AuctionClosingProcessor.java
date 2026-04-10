package com.cos.fairbid.winning.application.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.TopBidderInfo;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.trade.application.port.out.DeliveryInfoRepositoryPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.DeliveryInfo;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;

/**
 * 경매 종료 처리 서비스
 * 경매 종료 시 발생하는 비즈니스 로직을 담당
 *
 * Port 의존성이 있으므로 application 계층에 위치
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingProcessor {

    private final WinningRepositoryPort winningRepository;
    private final PushNotificationPort pushNotificationPort;
    private final TradeRepositoryPort tradeRepositoryPort;
    private final DeliveryInfoRepositoryPort deliveryInfoRepositoryPort;

    /**
     * 입찰자가 없는 경우 유찰 처리한다
     *
     * @param auction 유찰 처리할 경매
     */
    public void processNoWinner(Auction auction) {
        // 경매 유찰 처리
        auction.fail();

        // 판매자에게 유찰 알림
        pushNotificationPort.sendFailedAuctionNotification(
                auction.getSellerId(),
                auction.getId(),
                auction.getTitle()
        );

        log.info("경매 유찰 처리 완료 - auctionId: {}", auction.getId());
    }

    /**
     * 1순위 낙찰자를 처리한다
     *
     * @param auction      종료할 경매
     * @param topBidder    1순위 입찰자 정보 (Redis에서 조회)
     */
    public void processFirstRankWinner(Auction auction, TopBidderInfo topBidder) {
        Long bidderId = topBidder.bidderId();
        Long bidAmount = topBidder.bidAmount();

        // 1. 경매 종료 및 낙찰자 지정
        auction.close(bidderId);

        // 2. 1순위 Winning 저장
        Winning firstWinning = Winning.createFirstRank(
                auction.getId(),
                bidderId,
                bidAmount
        );
        winningRepository.save(firstWinning);

        // 3. Trade 생성 (기존 Transaction 대체)
        // Auction에서 거래 방식 정보를 가져와서 Trade 생성
        Trade trade = Trade.create(
                auction.getId(),
                auction.getSellerId(),
                bidderId,
                bidAmount,
                Boolean.TRUE.equals(auction.getDirectTradeAvailable()),
                Boolean.TRUE.equals(auction.getDeliveryAvailable())
        );
        Trade savedTrade = tradeRepositoryPort.save(trade);

        // 4. 택배 거래인 경우 DeliveryInfo 생성
        if (savedTrade.getMethod() == TradeMethod.DELIVERY) {
            DeliveryInfo deliveryInfo = DeliveryInfo.create(savedTrade.getId());
            deliveryInfoRepositoryPort.save(deliveryInfo);
        }

        log.debug("Trade 생성 완료 - auctionId: {}, buyerId: {}, method: {}",
                auction.getId(), bidderId, savedTrade.getMethod());

        // 5. 1순위 낙찰자에게 Push 알림
        pushNotificationPort.sendWinningNotification(
                bidderId,
                auction.getId(),
                auction.getTitle(),
                bidAmount
        );
    }

    /**
     * 2순위 후보를 저장한다
     *
     * @param auction       경매
     * @param secondBidder  2순위 입찰자 정보 (Redis에서 조회)
     */
    public void saveSecondRankCandidate(Auction auction, TopBidderInfo secondBidder) {
        Long bidderId = secondBidder.bidderId();
        Long bidAmount = secondBidder.bidAmount();

        Winning secondWinning = Winning.createSecondRank(
                auction.getId(),
                bidderId,
                bidAmount
        );
        winningRepository.save(secondWinning);

        // 2순위에게 대기 알림 발송
        pushNotificationPort.sendSecondRankStandbyNotification(
                bidderId,
                auction.getId(),
                auction.getTitle(),
                bidAmount
        );

        log.debug("2순위 후보 저장 - auctionId: {}, bidderId: {}", auction.getId(), bidderId);
    }
}
