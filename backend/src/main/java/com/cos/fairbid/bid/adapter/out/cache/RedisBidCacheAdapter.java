package com.cos.fairbid.bid.adapter.out.cache;

import com.cos.fairbid.bid.application.port.out.BidCachePort;
import com.cos.fairbid.bid.domain.exception.BidTooLowException;
import com.cos.fairbid.bid.domain.exception.AuctionEndedException;
import com.cos.fairbid.bid.domain.exception.InstantBuyException;
import com.cos.fairbid.bid.domain.exception.SelfBidNotAllowedException;

import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Redis Lua 스크립트 기반 입찰 캐시 어댑터
 * 원자적 입찰 처리로 동시성 제어
 */
@Component
@RequiredArgsConstructor
public class RedisBidCacheAdapter implements BidCachePort {

    private static final String AUCTION_KEY_PREFIX = "auction:";
    private static final String CLOSING_QUEUE_KEY = "auction:closing";

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<List> bidScript;

    @PostConstruct
    public void init() {
        // 입찰 처리 Lua 스크립트 로드
        bidScript = new DefaultRedisScript<>();
        bidScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/bid.lua")));
        bidScript.setResultType(List.class);
    }

    @Override
    public boolean existsInCache(Long auctionId) {
        String key = AUCTION_KEY_PREFIX + auctionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public BidResult placeBidAtomic(Long auctionId, Long bidAmount, Long bidderId, String bidType, Long currentTimeMs) {
        String key = AUCTION_KEY_PREFIX + auctionId;

        // Lua 스크립트 실행 (KEYS: 경매 해시 키 + 종료 대기 큐)
        List<Object> result = redisTemplate.execute(
                bidScript,
                Arrays.asList(key, CLOSING_QUEUE_KEY),
                String.valueOf(bidAmount),
                String.valueOf(bidderId),
                bidType,
                String.valueOf(currentTimeMs)
        );

        if (result == null || result.isEmpty()) {
            throw AuctionNotFoundException.withId(auctionId);
        }

        // 결과 파싱
        Long successFlag = (Long) result.get(0);

        if (successFlag == 0) {
            // 실패 케이스
            String errorCode = (String) result.get(1);
            handleBidError(errorCode, auctionId, bidderId, bidAmount, result);
        }

        // 성공 케이스: {1, newCurrentPrice, newTotalBidCount, newBidIncrement, extended, extensionCount, scheduledEndTimeMs, instantBuyActivated}
        Long newCurrentPrice = (Long) result.get(1);
        Long newTotalBidCount = (Long) result.get(2);
        Long newBidIncrement = (Long) result.get(3);
        Long extended = (Long) result.get(4);
        Long extensionCount = (Long) result.get(5);
        Long scheduledEndTimeMs = (Long) result.get(6);
        Long instantBuyActivated = (Long) result.get(7);

        return new BidResult(
                newCurrentPrice,
                newTotalBidCount.intValue(),
                newBidIncrement,
                extended == 1L,
                extensionCount.intValue(),
                scheduledEndTimeMs,
                instantBuyActivated == 1L
        );
    }

    /**
     * Lua 스크립트 에러 코드를 도메인 예외로 변환
     */
    private void handleBidError(String errorCode, Long auctionId, Long bidderId, Long bidAmount, List<Object> result) {
        switch (errorCode) {
            case "NOT_FOUND" -> throw AuctionNotFoundException.withId(auctionId);
            case "NOT_ACTIVE", "AUCTION_ENDED" -> throw AuctionEndedException.forBid(auctionId);
            case "SELF_BID" -> throw SelfBidNotAllowedException.create();
            case "BID_TOO_LOW" -> {
                Long minBidAmount = (Long) result.get(3);
                throw BidTooLowException.belowMinimum(bidAmount, minBidAmount);
            }
            // 즉시 구매 관련 에러
            case "INSTANT_BUY_NOT_AVAILABLE" -> throw InstantBuyException.notAvailable(auctionId);
            case "INSTANT_BUY_DISABLED" -> throw InstantBuyException.disabled(auctionId);
            case "INSTANT_BUY_ALREADY_ACTIVATED" -> throw InstantBuyException.alreadyActivated(auctionId);
            default -> throw new RuntimeException("Unknown bid error: " + errorCode);
        }
    }
}
