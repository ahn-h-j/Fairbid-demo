package com.cos.fairbid.winning.adapter.in.scheduler;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.winning.application.port.in.ProcessNoShowUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 응답 만료 감시 스케줄러
 * 주기적으로 응답 기한이 만료된 낙찰 건을 처리하고,
 * 응답 마감 임박 시 리마인더 알림을 발송한다
 *
 * Trade 기반 시스템 (24시간 응답 기한)
 *
 * server.role=api 또는 all에서만 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class PaymentTimeoutScheduler {

    private final ProcessNoShowUseCase processNoShowUseCase;
    private final TradeRepositoryPort tradeRepositoryPort;
    private final PushNotificationPort pushNotificationPort;
    private final AuctionRepositoryPort auctionRepositoryPort;

    /**
     * 1분마다 실행되어 응답 기한 만료 건을 처리한다
     * fixedDelay = 60000ms (1분) - 이전 작업 완료 후 1분 대기
     */
    @Scheduled(fixedDelay = 60000)
    public void checkResponseTimeouts() {
        try {
            processNoShowUseCase.processExpiredPayments();
        } catch (Exception e) {
            log.error("응답 만료 감시 스케줄러 실행 중 오류 발생", e);
        }
    }

    /**
     * 1분마다 실행되어 응답 마감 임박 리마인더를 발송한다
     * 응답 마감 12시간 전에 구매자에게 알림 발송
     * fixedDelay = 60000ms (1분) - 이전 작업 완료 후 1분 대기
     */
    @Scheduled(fixedDelay = 60000)
    public void sendResponseReminders() {
        try {
            // 리마인더 발송 대상 조회
            List<Trade> reminderTargets = tradeRepositoryPort.findReminderTargets();

            if (reminderTargets.isEmpty()) {
                return;
            }

            log.info("응답 리마인더 발송 대상: {}건", reminderTargets.size());

            for (Trade trade : reminderTargets) {
                try {
                    // 경매 정보 조회하여 제목 획득
                    Auction auction = auctionRepositoryPort.findById(trade.getAuctionId())
                            .orElse(null);

                    String auctionTitle = (auction != null) ? auction.getTitle() : "경매";

                    // 리마인더 알림 발송 (구매자에게)
                    pushNotificationPort.sendResponseReminderNotification(
                            trade.getBuyerId(),
                            trade.getAuctionId(),
                            auctionTitle,
                            trade.getFinalPrice()
                    );

                    // 리마인더 발송됨 표시 (중복 발송 방지)
                    trade.markReminderSent();
                    tradeRepositoryPort.save(trade);

                    log.debug("응답 리마인더 발송 완료 - auctionId: {}, buyerId: {}",
                            trade.getAuctionId(), trade.getBuyerId());
                } catch (Exception e) {
                    log.error("응답 리마인더 발송 실패 - auctionId: {}, buyerId: {}",
                            trade.getAuctionId(), trade.getBuyerId(), e);
                }
            }
        } catch (Exception e) {
            log.error("응답 리마인더 스케줄러 실행 중 오류 발생", e);
        }
    }
}
