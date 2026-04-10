package com.cos.fairbid.trade.adapter.in.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;
import com.cos.fairbid.trade.domain.TradeStatus;

/**
 * 거래 응답 DTO
 */
@Getter
@Builder
public class TradeResponse {

    private Long id;
    private Long auctionId;
    private Long sellerId;
    private Long buyerId;
    private Long finalPrice;
    private TradeStatus status;
    private TradeMethod method;
    private LocalDateTime responseDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static TradeResponse from(Trade trade) {
        return TradeResponse.builder()
                .id(trade.getId())
                .auctionId(trade.getAuctionId())
                .sellerId(trade.getSellerId())
                .buyerId(trade.getBuyerId())
                .finalPrice(trade.getFinalPrice())
                .status(trade.getStatus())
                .method(trade.getMethod())
                .responseDeadline(trade.getResponseDeadline())
                .createdAt(trade.getCreatedAt())
                .completedAt(trade.getCompletedAt())
                .build();
    }
}
