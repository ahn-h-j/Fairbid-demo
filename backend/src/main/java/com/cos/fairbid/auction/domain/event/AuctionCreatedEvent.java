package com.cos.fairbid.auction.domain.event;

import java.time.LocalDateTime;

import lombok.Getter;

import com.cos.fairbid.auction.domain.Auction;

/**
 * 경매 생성 이벤트
 * 경매가 생성된 후 발행되며, 캐시 워밍 등 후처리에 활용
 */
@Getter
public class AuctionCreatedEvent {

    private final Auction auction;
    private final LocalDateTime occurredAt;

    private AuctionCreatedEvent(Auction auction) {
        this.auction = auction;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 경매 생성 이벤트 생성
     *
     * @param auction 생성된 경매 도메인 객체
     * @return AuctionCreatedEvent 인스턴스
     */
    public static AuctionCreatedEvent of(Auction auction) {
        return new AuctionCreatedEvent(auction);
    }
}
