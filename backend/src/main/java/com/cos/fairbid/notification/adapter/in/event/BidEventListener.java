package com.cos.fairbid.notification.adapter.in.event;

import com.cos.fairbid.bid.domain.event.BidPlacedEvent;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;
import com.cos.fairbid.notification.dto.BidUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 입찰 이벤트 리스너
 * BidPlacedEvent를 구독하여 Redis Pub/Sub으로 브로드캐스트
 *
 * server.role=api 또는 all에서만 활성화.
 * 입찰은 API 서버에서만 발생하므로, WS 서버에서는 이 리스너가 불필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class BidEventListener {

    private final AuctionBroadcastPort auctionBroadcastPort;

    /**
     * 입찰 완료 이벤트 처리
     * Redis Lua 스크립트 기반 입찰은 트랜잭션 없이 동작하므로 @EventListener 사용
     *
     * @param event 입찰 완료 이벤트
     */
    @EventListener
    public void handleBidPlacedEvent(BidPlacedEvent event) {
        log.debug("BidPlacedEvent 수신: auctionId={}, currentPrice={}", event.getAuctionId(), event.getCurrentPrice());

        try {
            auctionBroadcastPort.broadcastBidUpdate(BidUpdateMessage.from(event));
        } catch (Exception e) {
            log.error("BidUpdateMessage 브로드캐스트 실패: auctionId={}", event.getAuctionId(), e);
        }
    }
}
