package com.cos.fairbid.bid.adapter.in.dto;

import java.time.LocalDateTime;

import lombok.Builder;

import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.bid.domain.BidType;

/**
 * 입찰 응답 DTO
 */
@Builder
public record BidResponse(
        Long id,
        Long auctionId,
        Long bidderId,
        Long amount,
        BidType bidType,
        LocalDateTime createdAt
) {
    /**
     * Domain → Response DTO 변환
     *
     * @param bid 입찰 도메인 객체
     * @return BidResponse
     */
    public static BidResponse from(Bid bid) {
        return BidResponse.builder()
                .id(bid.getId())
                .auctionId(bid.getAuctionId())
                .bidderId(bid.getBidderId())
                .amount(bid.getAmount())
                .bidType(bid.getBidType())
                .createdAt(bid.getCreatedAt())
                .build();
    }
}
