package com.cos.fairbid.winning.application.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeStatus;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;

/**
 * 노쇼 처리 서비스
 * 응답 기한 만료 시 발생하는 비즈니스 로직을 담당
 *
 * Port 의존성이 있으므로 application 계층에 위치
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowProcessor {

    private final WinningRepositoryPort winningRepository;
    private final AuctionRepositoryPort auctionRepository;
    private final PushNotificationPort pushNotificationPort;
    private final TradeRepositoryPort tradeRepositoryPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;

    /**
     * 1순위 노쇼를 처리한다
     * 노쇼 처리 후 2순위 승계 여부를 확인하여 처리한다
     *
     * @param firstWinning 1순위 낙찰 정보
     * @param auction      경매
     */
    public void processFirstRankNoShow(Winning firstWinning, Auction auction) {
        Long auctionId = auction.getId();

        // 1. 1순위 노쇼 처리
        firstWinning.markAsNoShow();
        winningRepository.save(firstWinning);

        // 2. 경고 부여
        Long bidderId = firstWinning.getBidderId();
        loadUserPort.findById(bidderId).ifPresent(user -> {
            user.addWarning();
            saveUserPort.save(user);
            log.info("노쇼 경고 부여 - userId: {}, 현재 경고 횟수: {}", bidderId, user.getWarningCount());
        });
        log.info("1순위 노쇼 처리 - auctionId: {}, bidderId: {}", auctionId, bidderId);

        // 3. 2순위 승계 여부 확인
        Optional<Winning> secondWinningOpt = winningRepository.findByAuctionIdAndRank(auctionId, 2);

        if (secondWinningOpt.isPresent()) {
            Winning secondWinning = secondWinningOpt.get();

            // 2순위가 90% 이상이면 자동 승계
            if (secondWinning.isEligibleForAutoTransfer(firstWinning.getBidAmount())) {
                // 노쇼 알림을 1순위에게 발송 (승계됨)
                pushNotificationPort.sendNoShowPenaltyNotification(
                        bidderId,
                        auctionId,
                        auction.getTitle()
                );
                transferToSecondRank(secondWinning, auction);
                return;
            }
        }

        // 4. 승계 불가 → Trade 취소 후 유찰 처리
        // 노쇼 알림을 1순위에게 발송 (유찰됨)
        pushNotificationPort.sendNoShowPenaltyNotification(
                bidderId,
                auctionId,
                auction.getTitle()
        );

        Optional<Trade> tradeOpt = tradeRepositoryPort.findByAuctionId(auctionId);
        tradeOpt.ifPresent(trade -> {
            trade.cancel();
            tradeRepositoryPort.save(trade);
            log.debug("Trade 취소 (1순위 노쇼, 승계 불가) - auctionId: {}", auctionId);
        });

        failAuction(auction);
    }

    /**
     * 2순위 승계를 처리한다
     *
     * @param secondWinning 2순위 낙찰 정보
     * @param auction       경매
     */
    public void transferToSecondRank(Winning secondWinning, Auction auction) {
        // 1. 2순위에게 응답 권한 부여 (12시간)
        secondWinning.transferToSecondRank();
        winningRepository.save(secondWinning);

        // 2. 경매 낙찰자 변경
        auction.transferWinner(secondWinning.getBidderId());
        auctionRepository.save(auction);

        // 3. Trade 2순위 승계 처리
        Optional<Trade> tradeOpt = tradeRepositoryPort.findByAuctionId(auction.getId());
        tradeOpt.ifPresent(trade -> {
            trade.transferToSecondRank(
                    secondWinning.getBidderId(),
                    secondWinning.getBidAmount()
            );
            tradeRepositoryPort.save(trade);
            log.debug("Trade 2순위 승계 - auctionId: {}, newBuyerId: {}", auction.getId(), secondWinning.getBidderId());
        });

        // 4. 2순위에게 승계 알림
        pushNotificationPort.sendTransferNotification(
                secondWinning.getBidderId(),
                auction.getId(),
                auction.getTitle(),
                secondWinning.getBidAmount()
        );

        log.info("2순위 승계 완료 - auctionId: {}, newWinnerId: {}", auction.getId(), secondWinning.getBidderId());
    }

    /**
     * 2순위 만료를 처리한다 (승계 후 미응답)
     * 비즈니스 규칙: 2순위 승계 후 미응답은 노쇼 처리 안함
     *
     * @param secondWinning 2순위 낙찰 정보
     * @param auction       경매
     */
    public void processSecondRankExpired(Winning secondWinning, Auction auction) {
        // 상태만 FAILED로 변경 (노쇼 처리 안함)
        secondWinning.markAsFailed();
        winningRepository.save(secondWinning);

        // Trade 취소 처리
        Optional<Trade> tradeOpt = tradeRepositoryPort.findByAuctionId(auction.getId());
        tradeOpt.ifPresent(trade -> {
            trade.cancel();
            tradeRepositoryPort.save(trade);
            log.debug("Trade 취소 (2순위 만료) - auctionId: {}", auction.getId());
        });

        // 경매 유찰 처리
        failAuction(auction);

        log.info("2순위 만료 → 유찰 처리 - auctionId: {}", auction.getId());
    }

    /**
     * 경매를 유찰 처리한다
     * Trade가 존재하고 완료/취소 상태가 아닌 경우 취소 처리한다
     *
     * @param auction 유찰 처리할 경매
     */
    public void failAuction(Auction auction) {
        auction.fail();
        auctionRepository.save(auction);

        // Trade가 존재하고 아직 완료/취소 상태가 아니라면 취소 처리
        Optional<Trade> tradeOpt = tradeRepositoryPort.findByAuctionId(auction.getId());
        tradeOpt.ifPresent(trade -> {
            if (trade.getStatus() != TradeStatus.COMPLETED && trade.getStatus() != TradeStatus.CANCELLED) {
                trade.cancel();
                tradeRepositoryPort.save(trade);
                log.debug("Trade 취소 (유찰) - auctionId: {}", auction.getId());
            }
        });

        // 판매자에게 유찰 알림
        pushNotificationPort.sendFailedAuctionNotification(
                auction.getSellerId(),
                auction.getId(),
                auction.getTitle()
        );

        log.info("경매 유찰 처리 완료 - auctionId: {}", auction.getId());
    }
}
