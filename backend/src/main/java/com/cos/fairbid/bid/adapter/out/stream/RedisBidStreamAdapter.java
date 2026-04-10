package com.cos.fairbid.bid.adapter.out.stream;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.bid.application.port.out.BidStreamPort;
import com.cos.fairbid.bid.domain.Bid;

/**
 * Redis Stream 기반 입찰 RDB 동기화 메시지 발행 어댑터
 *
 * 입찰 처리 후 RDB 동기화 데이터를 Redis Stream(XADD)에 발행한다.
 * Consumer Group이 이 메시지를 소비하여 RDB에 저장한다.
 *
 * XADD는 Redis 메모리 내 O(1) 연산이므로 DB 상태와 무관하게
 * 일정한 응답 시간(~0.1ms)을 보장한다.
 */
@Component
@Slf4j
public class RedisBidStreamAdapter implements BidStreamPort {

    /** Redis Stream 키 */
    public static final String STREAM_KEY = "stream:bid-rdb-sync";

    private final StringRedisTemplate redisTemplate;
    private final Counter publishSuccessCounter;
    private final Counter publishFailCounter;

    public RedisBidStreamAdapter(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.publishSuccessCounter = Counter.builder("fairbid_stream_publish_total")
                .tag("result", "success")
                .description("Stream 메시지 발행 성공 건수")
                .register(meterRegistry);
        this.publishFailCounter = Counter.builder("fairbid_stream_publish_total")
                .tag("result", "fail")
                .description("Stream 메시지 발행 실패 건수")
                .register(meterRegistry);
    }

    @Override
    public String publishBidSave(Bid bid) {
        Map<String, String> message = new HashMap<>();
        message.put("type", "BID_SAVE");
        message.put("auctionId", String.valueOf(bid.getAuctionId()));
        message.put("bidderId", String.valueOf(bid.getBidderId()));
        message.put("amount", String.valueOf(bid.getAmount()));
        message.put("bidType", bid.getBidType().name());
        message.put("createdAt", bid.getCreatedAt().toString());

        return publish(message, "BID_SAVE", bid.getAuctionId());
    }

    @Override
    public String publishInstantBuyUpdate(
            Long auctionId, Long currentPrice, Integer totalBidCount,
            Long bidIncrement, Long bidderId, Long currentTimeMs, Long scheduledEndTimeMs
    ) {
        Map<String, String> message = new HashMap<>();
        message.put("type", "INSTANT_BUY_UPDATE");
        message.put("auctionId", String.valueOf(auctionId));
        message.put("currentPrice", String.valueOf(currentPrice));
        message.put("totalBidCount", String.valueOf(totalBidCount));
        message.put("bidIncrement", String.valueOf(bidIncrement));
        message.put("bidderId", String.valueOf(bidderId));
        message.put("currentTimeMs", String.valueOf(currentTimeMs));
        message.put("scheduledEndTimeMs", String.valueOf(scheduledEndTimeMs));

        return publish(message, "INSTANT_BUY_UPDATE", auctionId);
    }

    /**
     * Redis Stream에 메시지를 발행한다 (XADD).
     *
     * @param message 메시지 필드 맵
     * @param type    메시지 타입 (로깅용)
     * @param auctionId 경매 ID (로깅용)
     * @return Record ID 문자열, 실패 시 null
     */
    private String publish(Map<String, String> message, String type, Long auctionId) {
        try {
            StringRecord record = StreamRecords.string(message).withStreamKey(STREAM_KEY);
            RecordId recordId = redisTemplate.opsForStream().add(record);

            publishSuccessCounter.increment();
            log.debug("Stream 메시지 발행: type={}, auctionId={}, recordId={}",
                    type, auctionId, recordId);
            return recordId != null ? recordId.getValue() : null;
        } catch (Exception e) {
            publishFailCounter.increment();
            log.error("Stream 메시지 발행 실패: type={}, auctionId={}, error={}",
                    type, auctionId, e.getMessage());
            return null;
        }
    }
}
