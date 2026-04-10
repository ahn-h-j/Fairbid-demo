package com.cos.fairbid.winning.application.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;

/**
 * 경매 종료 이벤트 리스너
 * 트랜잭션 커밋 후 Redis Pub/Sub으로 경매 종료 브로드캐스트
 *
 * server.role=api 또는 all에서만 활성화.
 * 경매 종료 처리는 API 서버에서만 발생한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class AuctionClosedEventListener {

    private final AuctionBroadcastPort auctionBroadcastPort;

    /**
     * 경매 종료 이벤트 처리
     * 트랜잭션 커밋 후(AFTER_COMMIT) 실행되어 롤백 시 브로드캐스트되지 않음
     *
     * @param event 경매 종료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        try {
            auctionBroadcastPort.broadcastAuctionClosed(event.auctionId());
            log.debug("경매 종료 브로드캐스트 완료 - auctionId: {}", event.auctionId());
        } catch (Exception e) {
            log.error("경매 종료 브로드캐스트 실패 - auctionId: {}", event.auctionId(), e);
        }
    }
}
