package com.cos.fairbid.trade.application.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.trade.application.port.in.DirectTradeUseCase;
import com.cos.fairbid.trade.application.port.out.DirectTradeInfoRepositoryPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.DirectTradeInfo;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;
import com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException;
import com.cos.fairbid.trade.domain.exception.NotTradeParticipantException;
import com.cos.fairbid.trade.domain.exception.TradeNotFoundException;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;

/**
 * 직거래 서비스
 * DirectTradeUseCase 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DirectTradeService implements DirectTradeUseCase {

    private final TradeRepositoryPort tradeRepositoryPort;
    private final DirectTradeInfoRepositoryPort directTradeInfoRepositoryPort;
    private final AuctionRepositoryPort auctionRepositoryPort;
    private final WinningRepositoryPort winningRepositoryPort;
    private final PushNotificationPort pushNotificationPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<DirectTradeInfo> findByTradeId(Long tradeId) {
        return directTradeInfoRepositoryPort.findByTradeId(tradeId);
    }

    @Override
    public DirectTradeInfo propose(Long tradeId, Long userId, LocalDate meetingDate, LocalTime meetingTime) {
        Trade trade = findTradeOrThrow(tradeId);

        // 판매자만 첫 제안 가능
        if (!trade.isSeller(userId)) {
            throw NotTradeParticipantException.notSeller(userId, tradeId);
        }

        // 직거래인지 확인
        if (trade.getMethod() != TradeMethod.DIRECT) {
            throw InvalidTradeStatusException.notDirectTrade(tradeId);
        }

        // 이미 직거래 정보가 있는지 확인
        Optional<DirectTradeInfo> existingInfo = directTradeInfoRepositoryPort.findByTradeId(tradeId);
        if (existingInfo.isPresent()) {
            throw new IllegalStateException("이미 직거래 제안이 존재합니다. 역제안을 사용하세요.");
        }

        // Auction에서 위치 정보 조회
        Auction auction = auctionRepositoryPort.findById(trade.getAuctionId())
                .orElseThrow(() -> AuctionNotFoundException.withId(trade.getAuctionId()));

        // 직거래 정보 생성
        DirectTradeInfo directTradeInfo = DirectTradeInfo.create(
                tradeId,
                auction.getDirectTradeLocation(),
                meetingDate,
                meetingTime,
                userId
        );
        DirectTradeInfo savedInfo = directTradeInfoRepositoryPort.save(directTradeInfo);

        // Winning 응답 완료 처리 (판매자가 먼저 제안하면 응답한 것으로 간주)
        markWinningAsResponded(trade.getAuctionId(), trade.getBuyerId());

        // 구매자에게 직거래 일정 제안 알림
        pushNotificationPort.sendArrangementProposedNotification(
                trade.getBuyerId(),
                auction.getId(),
                tradeId,
                auction.getTitle()
        );

        log.info("직거래 시간 제안 - tradeId: {}, date: {}, time: {}", tradeId, meetingDate, meetingTime);
        return savedInfo;
    }

    @Override
    public DirectTradeInfo accept(Long tradeId, Long userId) {
        Trade trade = findTradeOrThrow(tradeId);

        // 거래 참여자 확인
        if (!trade.isParticipant(userId)) {
            throw NotTradeParticipantException.notParticipant(userId, tradeId);
        }

        // 직거래 정보 조회
        DirectTradeInfo directTradeInfo = directTradeInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("직거래 제안이 없습니다."));

        // 제안자 본인은 수락 불가 (상대방만 수락 가능)
        if (directTradeInfo.getProposedBy().equals(userId)) {
            throw new IllegalStateException("본인이 제안한 시간은 본인이 수락할 수 없습니다.");
        }

        // 수락 처리
        directTradeInfo.accept();
        DirectTradeInfo savedInfo = directTradeInfoRepositoryPort.save(directTradeInfo);

        // Trade 조율 완료 처리
        trade.markArranged();
        tradeRepositoryPort.save(trade);

        // 제안자에게 수락 알림
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendArrangementAcceptedNotification(
                    directTradeInfo.getProposedBy(),
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("직거래 수락 - tradeId: {}, acceptedBy: {}", tradeId, userId);
        return savedInfo;
    }

    @Override
    public DirectTradeInfo counterPropose(Long tradeId, Long userId, LocalDate meetingDate, LocalTime meetingTime) {
        Trade trade = findTradeOrThrow(tradeId);

        // 거래 참여자 확인
        if (!trade.isParticipant(userId)) {
            throw NotTradeParticipantException.notParticipant(userId, tradeId);
        }

        // 직거래 정보 조회
        DirectTradeInfo directTradeInfo = directTradeInfoRepositoryPort.findByTradeId(tradeId)
                .orElseThrow(() -> new IllegalStateException("직거래 제안이 없습니다. 먼저 제안을 해주세요."));

        // 역제안 처리
        directTradeInfo.counterPropose(meetingDate, meetingTime, userId);
        DirectTradeInfo savedInfo = directTradeInfoRepositoryPort.save(directTradeInfo);

        // 상대방에게 역제안 알림
        Long recipientId = trade.isSeller(userId) ? trade.getBuyerId() : trade.getSellerId();
        auctionRepositoryPort.findById(trade.getAuctionId()).ifPresent(auction -> {
            pushNotificationPort.sendArrangementCounterProposedNotification(
                    recipientId,
                    auction.getId(),
                    tradeId,
                    auction.getTitle()
            );
        });

        log.info("직거래 역제안 - tradeId: {}, date: {}, time: {}", tradeId, meetingDate, meetingTime);
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
