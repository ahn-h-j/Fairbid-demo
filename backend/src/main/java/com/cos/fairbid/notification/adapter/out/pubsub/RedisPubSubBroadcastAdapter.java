package com.cos.fairbid.notification.adapter.out.pubsub;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;
import com.cos.fairbid.notification.dto.AuctionClosedMessage;
import com.cos.fairbid.notification.dto.BidUpdateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub을 통한 경매 브로드캐스트 어댑터 (발행 전용)
 *
 * 입찰/종료 메시지를 Redis 채널에 발행하여 모든 서버 인스턴스가 수신할 수 있게 한다.
 * 각 서버의 RedisMessageSubscriber가 메시지를 받아 로컬 WebSocket 구독자에게 전달한다.
 *
 * [입찰 발생] → [이 어댑터: Redis 발행] → [Redis Pub/Sub] → [각 서버: RedisMessageSubscriber] → [SimpMessagingTemplate] → [WS 구독자]
 *
 * server.role=api 또는 all에서만 활성화.
 * WS 서버는 메시지를 구독만 하고, 발행은 API 서버가 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class RedisPubSubBroadcastAdapter implements AuctionBroadcastPort {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis Pub/Sub 채널명 */
    public static final String CHANNEL_BID_UPDATE = "auction:bid-update";
    public static final String CHANNEL_AUCTION_CLOSED = "auction:closed";

    /**
     * 입찰 업데이트 메시지를 Redis Pub/Sub에 발행
     * 모든 서버 인스턴스가 이 메시지를 수신하여 각자의 WebSocket 구독자에게 전달한다.
     */
    @Override
    public void broadcastBidUpdate(BidUpdateMessage message) {
        if (message == null) {
            log.warn("BidUpdateMessage is null, skipping broadcast");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL_BID_UPDATE, json);
            log.info("Published bid update to Redis Pub/Sub: auctionId={}, currentPrice={}",
                    message.auctionId(), message.currentPrice());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize BidUpdateMessage: {}", e.getMessage(), e);
        }
    }

    /**
     * 경매 종료 메시지를 Redis Pub/Sub에 발행
     */
    @Override
    public void broadcastAuctionClosed(Long auctionId) {
        if (auctionId == null) {
            log.warn("auctionId is null, skipping auction closed broadcast");
            return;
        }

        try {
            AuctionClosedMessage message = AuctionClosedMessage.of(auctionId);
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL_AUCTION_CLOSED, json);
            log.info("Published auction closed to Redis Pub/Sub: auctionId={}", auctionId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AuctionClosedMessage: {}", e.getMessage(), e);
        }
    }
}
