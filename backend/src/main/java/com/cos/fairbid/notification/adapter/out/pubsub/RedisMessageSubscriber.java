package com.cos.fairbid.notification.adapter.out.pubsub;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.notification.dto.AuctionClosedMessage;
import com.cos.fairbid.notification.dto.BidUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 메시지 수신자
 *
 * Redis 채널에서 메시지를 수신하여 이 서버의 WebSocket 구독자에게 전달한다.
 * 모든 서버 인스턴스에서 동일하게 동작하므로, 어떤 서버에서 입찰이 발생하든
 * 모든 서버의 구독자가 메시지를 받을 수 있다.
 *
 * server.role=ws 또는 all에서만 활성화.
 * API 서버는 메시지를 발행만 하고, 구독(수신→WebSocket 전달)은 WS 서버만 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnabledOnRole({"ws", "all"})
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /** WebSocket 토픽 경로 패턴 */
    private static final String AUCTION_TOPIC_FORMAT = "/topic/auctions/%d";

    /**
     * Redis Pub/Sub 메시지 수신 콜백
     * 채널에 따라 BidUpdateMessage 또는 AuctionClosedMessage를 파싱하여
     * 해당 경매의 WebSocket 구독자에게 전달한다.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            if (RedisPubSubBroadcastAdapter.CHANNEL_BID_UPDATE.equals(channel)) {
                BidUpdateMessage bidUpdate = objectMapper.readValue(body, BidUpdateMessage.class);
                String destination = String.format(AUCTION_TOPIC_FORMAT, bidUpdate.auctionId());
                messagingTemplate.convertAndSend(destination, bidUpdate);
                log.debug("Relayed bid update to WebSocket: auctionId={}, currentPrice={}",
                        bidUpdate.auctionId(), bidUpdate.currentPrice());

            } else if (RedisPubSubBroadcastAdapter.CHANNEL_AUCTION_CLOSED.equals(channel)) {
                AuctionClosedMessage closedMessage = objectMapper.readValue(body, AuctionClosedMessage.class);
                String destination = String.format(AUCTION_TOPIC_FORMAT, closedMessage.auctionId());
                messagingTemplate.convertAndSend(destination, closedMessage);
                log.debug("Relayed auction closed to WebSocket: auctionId={}", closedMessage.auctionId());
            }
        } catch (Exception e) {
            log.error("Failed to process Redis Pub/Sub message: channel={}, error={}", channel, e.getMessage(), e);
        }
    }
}
