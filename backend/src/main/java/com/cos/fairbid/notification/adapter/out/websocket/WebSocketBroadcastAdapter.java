package com.cos.fairbid.notification.adapter.out.websocket;

import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;
import com.cos.fairbid.notification.dto.AuctionClosedMessage;
import com.cos.fairbid.notification.dto.BidUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * WebSocket을 통한 경매 브로드캐스트 어댑터 (단일 서버용, 현재 미사용)
 *
 * 스케일아웃 환경에서는 RedisPubSubBroadcastAdapter가 AuctionBroadcastPort를 대체한다.
 * 단일 서버에서만 사용할 경우 @Component를 다시 활성화하면 된다.
 */
@Slf4j
// @Component — RedisPubSubBroadcastAdapter로 교체됨
@RequiredArgsConstructor
public class WebSocketBroadcastAdapter implements AuctionBroadcastPort {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 토픽 경로 패턴
     * /topic/auctions/{auctionId}
     */
    private static final String AUCTION_TOPIC_FORMAT = "/topic/auctions/%d";

    /**
     * 입찰 업데이트 메시지를 해당 경매 구독자들에게 브로드캐스트
     *
     * @param message 전송할 입찰 업데이트 메시지
     */
    @Override
    public void broadcastBidUpdate(BidUpdateMessage message) {
        if (message == null) {
            log.warn("BidUpdateMessage is null, skipping broadcast");
            return;
        }

        String destination = String.format(AUCTION_TOPIC_FORMAT, message.auctionId());

        log.info("Broadcasting bid update to {}: currentPrice={}, extended={}",
                destination, message.currentPrice(), message.extended());

        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 경매 종료 메시지를 해당 경매 구독자들에게 브로드캐스트
     *
     * @param auctionId 종료된 경매 ID
     */
    @Override
    public void broadcastAuctionClosed(Long auctionId) {
        if (auctionId == null) {
            log.warn("auctionId is null, skipping auction closed broadcast");
            return;
        }

        String destination = String.format(AUCTION_TOPIC_FORMAT, auctionId);
        AuctionClosedMessage message = AuctionClosedMessage.of(auctionId);

        log.info("Broadcasting auction closed to {}", destination);

        messagingTemplate.convertAndSend(destination, message);
    }
}
