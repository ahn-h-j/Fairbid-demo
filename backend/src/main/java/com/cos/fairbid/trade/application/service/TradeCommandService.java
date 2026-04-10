package com.cos.fairbid.trade.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.trade.application.port.in.TradeCommandUseCase;
import com.cos.fairbid.trade.application.port.out.DeliveryInfoRepositoryPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.DeliveryInfo;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;
import com.cos.fairbid.trade.domain.TradeStatus;
import com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException;
import com.cos.fairbid.trade.domain.exception.NotTradeParticipantException;
import com.cos.fairbid.trade.domain.exception.TradeNotFoundException;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;

/**
 * 거래 명령 서비스
 * TradeCommandUseCase 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TradeCommandService implements TradeCommandUseCase {

    private final TradeRepositoryPort tradeRepositoryPort;
    private final DeliveryInfoRepositoryPort deliveryInfoRepositoryPort;
    private final WinningRepositoryPort winningRepositoryPort;
    private final AuctionRepositoryPort auctionRepositoryPort;
    private final PushNotificationPort pushNotificationPort;

    @Override
    public Trade selectMethod(Long tradeId, Long userId, TradeMethod method) {
        Trade trade = findTradeOrThrow(tradeId);

        // 구매자만 거래 방식 선택 가능
        if (!trade.isBuyer(userId)) {
            throw NotTradeParticipantException.notBuyer(userId, tradeId);
        }

        // 거래 방식 선택 대기 상태 확인
        if (trade.getStatus() != TradeStatus.AWAITING_METHOD_SELECTION) {
            throw InvalidTradeStatusException.cannotSelectMethod(trade.getStatus());
        }

        // 거래 방식 선택
        trade.selectMethod(method);
        Trade savedTrade = tradeRepositoryPort.save(trade);

        // 택배 선택 시 DeliveryInfo 생성
        if (method == TradeMethod.DELIVERY) {
            DeliveryInfo deliveryInfo = DeliveryInfo.create(savedTrade.getId());
            deliveryInfoRepositoryPort.save(deliveryInfo);
            log.debug("택배 선택으로 DeliveryInfo 생성 - tradeId: {}", tradeId);
        }

        // Winning 응답 완료 처리
        markWinningAsResponded(trade.getAuctionId(), userId);

        // 판매자에게 거래 방식 선택 알림
        final Long savedTradeId = savedTrade.getId();
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendMethodSelectedNotification(
                    trade.getSellerId(),
                    auction.getId(),
                    savedTradeId,
                    auction.getTitle(),
                    method == TradeMethod.DIRECT
            );
        });

        log.info("거래 방식 선택 완료 - tradeId: {}, method: {}", tradeId, method);
        return savedTrade;
    }

    @Override
    public Trade complete(Long tradeId, Long userId) {
        Trade trade = findTradeOrThrow(tradeId);

        // 거래 참여자 확인
        if (!trade.isParticipant(userId)) {
            throw NotTradeParticipantException.notParticipant(userId, tradeId);
        }

        // 거래 완료 처리
        trade.complete();
        Trade savedTrade = tradeRepositoryPort.save(trade);

        // 양쪽에게 거래 완료 알림
        final Long completedTradeId = savedTrade.getId();
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendTradeCompletedNotification(
                    trade.getSellerId(),
                    auction.getId(),
                    completedTradeId,
                    auction.getTitle()
            );
            pushNotificationPort.sendTradeCompletedNotification(
                    trade.getBuyerId(),
                    auction.getId(),
                    completedTradeId,
                    auction.getTitle()
            );
        });

        log.info("거래 완료 - tradeId: {}, userId: {}", tradeId, userId);
        return savedTrade;
    }

    /**
     * 거래 조회 (없으면 예외)
     */
    private Trade findTradeOrThrow(Long tradeId) {
        return tradeRepositoryPort.findById(tradeId)
                .orElseThrow(() -> TradeNotFoundException.withId(tradeId));
    }

    /**
     * Winning 응답 완료 처리
     */
    private void markWinningAsResponded(Long auctionId, Long bidderId) {
        winningRepositoryPort.findPendingByAuctionIdAndBidderId(auctionId, bidderId)
                .ifPresent(winning -> {
                    winning.markAsResponded();
                    winningRepositoryPort.save(winning);
                    log.debug("Winning 응답 완료 처리 - auctionId: {}, bidderId: {}", auctionId, bidderId);
                });
    }
}
