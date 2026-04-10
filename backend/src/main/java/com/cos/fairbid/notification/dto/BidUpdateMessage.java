package com.cos.fairbid.notification.dto;

import java.time.LocalDateTime;

import com.cos.fairbid.bid.domain.event.BidPlacedEvent;

/**
 * WebSocket을 통해 클라이언트에 전송되는 입찰 업데이트 메시지
 * 경매 상세 페이지에서 실시간으로 현재가, 종료시간, 다음 입찰가, 입찰 단위, 총 입찰수, 현재 1순위 입찰자를 갱신하는 데 사용
 *
 * @param auctionId        경매 ID
 * @param currentPrice     현재가 (입찰 후 갱신된 가격)
 * @param scheduledEndTime 종료 예정 시간 (연장 시 갱신됨)
 * @param extended         경매 연장 여부
 * @param nextMinBidPrice  다음 최소 입찰 가능 금액
 * @param bidIncrement     입찰 단위 (가격 구간에 따라 재계산됨)
 * @param totalBidCount    총 입찰 횟수
 * @param topBidderId      현재 1순위 입찰자 ID
 * @param occurredAt       이벤트 발생 시간
 */
public record BidUpdateMessage(
        Long auctionId,
        Long currentPrice,
        LocalDateTime scheduledEndTime,
        boolean extended,
        Long nextMinBidPrice,
        Long bidIncrement,
        Integer totalBidCount,
        Long topBidderId,
        LocalDateTime occurredAt
) {
    /**
     * BidPlacedEvent로부터 메시지 생성
     *
     * @param event 입찰 완료 이벤트 (null 불가)
     * @return BidUpdateMessage 인스턴스
     * @throws IllegalArgumentException event가 null인 경우
     */
    public static BidUpdateMessage from(BidPlacedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("BidPlacedEvent must not be null");
        }
        return new BidUpdateMessage(
                event.getAuctionId(),
                event.getCurrentPrice(),
                event.getScheduledEndTime(),
                event.isExtended(),
                event.getNextMinBidPrice(),
                event.getBidIncrement(),
                event.getTotalBidCount(),
                event.getTopBidderId(),
                event.getOccurredAt()
        );
    }
}
