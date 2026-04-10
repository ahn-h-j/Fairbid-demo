package com.cos.fairbid.bid.domain.event;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * 입찰 완료 이벤트
 * 입찰이 성공적으로 처리된 후 발행되며, 실시간 UI 업데이트에 활용
 * (현재가, 종료시간, 다음 입찰가, 총 입찰수, 현재 1순위 입찰자 등 실시간 반영)
 */
@Getter
public class BidPlacedEvent {

    private final Long auctionId;
    private final Long currentPrice;
    private final LocalDateTime scheduledEndTime;
    private final boolean extended;
    private final Long nextMinBidPrice;
    private final Long bidIncrement;
    private final Integer totalBidCount;
    private final Long topBidderId;
    private final LocalDateTime occurredAt;

    private BidPlacedEvent(
            Long auctionId,
            Long currentPrice,
            LocalDateTime scheduledEndTime,
            boolean extended,
            Long nextMinBidPrice,
            Long bidIncrement,
            Integer totalBidCount,
            Long topBidderId
    ) {
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.scheduledEndTime = scheduledEndTime;
        this.extended = extended;
        this.nextMinBidPrice = nextMinBidPrice;
        this.bidIncrement = bidIncrement;
        this.totalBidCount = totalBidCount;
        this.topBidderId = topBidderId;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 입찰 완료 이벤트 생성
     *
     * @param auctionId        경매 ID
     * @param currentPrice     현재가 (입찰 후)
     * @param scheduledEndTime 종료 예정 시간 (연장 시 갱신됨)
     * @param extended         경매 연장 여부
     * @param nextMinBidPrice  다음 최소 입찰 가능 금액
     * @param bidIncrement     입찰 단위 (가격 구간에 따라 재계산됨)
     * @param totalBidCount    총 입찰 횟수
     * @param topBidderId      현재 1순위 입찰자 ID
     * @return BidPlacedEvent 인스턴스
     */
    public static BidPlacedEvent of(
            Long auctionId,
            Long currentPrice,
            LocalDateTime scheduledEndTime,
            boolean extended,
            Long nextMinBidPrice,
            Long bidIncrement,
            Integer totalBidCount,
            Long topBidderId
    ) {
        return new BidPlacedEvent(
                auctionId,
                currentPrice,
                scheduledEndTime,
                extended,
                nextMinBidPrice,
                bidIncrement,
                totalBidCount,
                topBidderId
        );
    }
}
