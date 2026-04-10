package com.cos.fairbid.trade.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.trade.application.port.in.DeliveryUseCase;
import com.cos.fairbid.trade.application.port.out.DeliveryInfoRepositoryPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.DeliveryInfo;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;
import com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException;
import com.cos.fairbid.trade.domain.exception.NotTradeParticipantException;
import com.cos.fairbid.trade.domain.exception.TradeNotFoundException;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;

/**
 * 택배 배송 서비스
 * DeliveryUseCase 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryService implements DeliveryUseCase {

    private final TradeRepositoryPort tradeRepositoryPort;
    private final DeliveryInfoRepositoryPort deliveryInfoRepositoryPort;
    private final WinningRepositoryPort winningRepositoryPort;
    private final AuctionRepositoryPort auctionRepositoryPort;
    private final PushNotificationPort pushNotificationPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryInfo> findByTradeId(Long tradeId) {
        return deliveryInfoRepositoryPort.findByTradeId(tradeId);
    }

    @Override
    public DeliveryInfo submitAddress(
            Long tradeId,
            Long userId,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    ) {
        Trade trade = findTradeOrThrow(tradeId);

        // 구매자만 배송지 입력 가능
        if (!trade.isBuyer(userId)) {
            throw NotTradeParticipantException.notBuyer(userId, tradeId);
        }

        // 택배인지 확인
        if (trade.getMethod() != TradeMethod.DELIVERY) {
            throw InvalidTradeStatusException.notDelivery(tradeId);
        }

        // 배송 정보 조회
        DeliveryInfo deliveryInfo = deliveryInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("배송 정보가 없습니다."));

        // 배송지 입력
        deliveryInfo.submitAddress(recipientName, recipientPhone, postalCode, address, addressDetail);
        DeliveryInfo savedInfo = deliveryInfoRepositoryPort.save(deliveryInfo);

        // Winning 응답 완료 처리
        markWinningAsResponded(trade.getAuctionId(), userId);

        // 판매자에게 배송지 입력 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendDeliveryAddressSubmittedNotification(
                    trade.getSellerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("배송지 입력 완료 - tradeId: {}, recipient: {}", tradeId, recipientName);
        return savedInfo;
    }

    @Override
    public DeliveryInfo ship(Long tradeId, Long userId, String courierCompany, String trackingNumber) {
        Trade trade = findTradeOrThrow(tradeId);

        // 판매자만 송장 입력 가능
        if (!trade.isSeller(userId)) {
            throw NotTradeParticipantException.notSeller(userId, tradeId);
        }

        // 배송 정보 조회
        DeliveryInfo deliveryInfo = deliveryInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("배송 정보가 없습니다."));

        // 발송 처리
        deliveryInfo.ship(courierCompany, trackingNumber);
        DeliveryInfo savedInfo = deliveryInfoRepositoryPort.save(deliveryInfo);

        // Trade 조율 완료 처리
        trade.markArranged();
        tradeRepositoryPort.save(trade);

        // 구매자에게 발송 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendDeliveryShippedNotification(
                    trade.getBuyerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("발송 완료 - tradeId: {}, courier: {}, tracking: {}", tradeId, courierCompany, trackingNumber);
        return savedInfo;
    }

    @Override
    public DeliveryInfo confirmPayment(Long tradeId, Long userId) {
        Trade trade = findTradeOrThrow(tradeId);

        // 구매자만 입금 확인 가능
        if (!trade.isBuyer(userId)) {
            throw NotTradeParticipantException.notBuyer(userId, tradeId);
        }

        // 택배 거래만 입금 확인 가능
        if (trade.getMethod() != TradeMethod.DELIVERY) {
            throw InvalidTradeStatusException.notDelivery(tradeId);
        }

        // 배송 정보 조회
        DeliveryInfo deliveryInfo = deliveryInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("배송 정보가 없습니다."));

        // 입금 확인 처리
        deliveryInfo.confirmPayment();
        DeliveryInfo savedInfo = deliveryInfoRepositoryPort.save(deliveryInfo);

        // 판매자에게 입금 완료 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendPaymentConfirmedNotification(
                    trade.getSellerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("입금 완료 처리 - tradeId: {}, buyerId: {}", tradeId, userId);
        return savedInfo;
    }

    @Override
    public DeliveryInfo verifyPayment(Long tradeId, Long userId) {
        Trade trade = findTradeOrThrow(tradeId);

        // 판매자만 입금 확인 가능
        if (!trade.isSeller(userId)) {
            throw NotTradeParticipantException.notSeller(userId, tradeId);
        }

        // 택배 거래만 입금 확인 가능
        if (trade.getMethod() != TradeMethod.DELIVERY) {
            throw InvalidTradeStatusException.notDelivery(tradeId);
        }

        // 배송 정보 조회
        DeliveryInfo deliveryInfo = deliveryInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("배송 정보가 없습니다."));

        // 입금 확인 처리
        deliveryInfo.verifyPayment();
        DeliveryInfo savedInfo = deliveryInfoRepositoryPort.save(deliveryInfo);

        // 구매자에게 입금 확인 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendPaymentVerifiedNotification(
                    trade.getBuyerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("입금 확인 완료 - tradeId: {}, sellerId: {}", tradeId, userId);
        return savedInfo;
    }

    @Override
    public DeliveryInfo rejectPayment(Long tradeId, Long userId) {
        Trade trade = findTradeOrThrow(tradeId);

        // 판매자만 미입금 처리 가능
        if (!trade.isSeller(userId)) {
            throw NotTradeParticipantException.notSeller(userId, tradeId);
        }

        // 택배 거래만 미입금 처리 가능
        if (trade.getMethod() != TradeMethod.DELIVERY) {
            throw InvalidTradeStatusException.notDelivery(tradeId);
        }

        // 배송 정보 조회
        DeliveryInfo deliveryInfo = deliveryInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("배송 정보가 없습니다."));

        // 미입금 처리
        deliveryInfo.rejectPayment();
        DeliveryInfo savedInfo = deliveryInfoRepositoryPort.save(deliveryInfo);

        // 구매자에게 미입금 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendPaymentRejectedNotification(
                    trade.getBuyerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("미입금 처리 - tradeId: {}, sellerId: {}", tradeId, userId);
        return savedInfo;
    }

    @Override
    public DeliveryInfo confirmDelivery(Long tradeId, Long userId) {
        Trade trade = findTradeOrThrow(tradeId);

        // 구매자만 수령 확인 가능
        if (!trade.isBuyer(userId)) {
            throw NotTradeParticipantException.notBuyer(userId, tradeId);
        }

        // 배송 정보 조회
        DeliveryInfo deliveryInfo = deliveryInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("배송 정보가 없습니다."));

        // 수령 확인
        deliveryInfo.confirmDelivery();
        DeliveryInfo savedInfo = deliveryInfoRepositoryPort.save(deliveryInfo);

        // 거래 완료 처리
        trade.complete();
        tradeRepositoryPort.save(trade);

        // 양쪽에게 거래 완료 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendTradeCompletedNotification(
                    trade.getSellerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
            pushNotificationPort.sendTradeCompletedNotification(
                    trade.getBuyerId(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("수령 확인 완료 - tradeId: {}", tradeId);
        return savedInfo;
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
