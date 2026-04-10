package com.cos.fairbid.auction.adapter.in.dto;

import java.time.LocalDateTime;

import lombok.Builder;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;

/**
 * 경매 목록 응답 DTO
 * 목록 조회용 간단한 정보만 포함
 */
@Builder
public record AuctionListResponse(
        Long id,
        String title,
        String thumbnailUrl,
        Long currentPrice,
        Long startPrice,
        Integer totalBidCount,
        AuctionStatus status,
        LocalDateTime scheduledEndTime
) {
    /**
     * Domain → Response DTO 변환
     *
     * @param auction 경매 도메인 객체
     * @return 경매 목록 응답 DTO
     */
    public static AuctionListResponse from(Auction auction) {
        return AuctionListResponse.builder()
                .id(auction.getId())
                .title(auction.getTitle())
                .thumbnailUrl(extractThumbnail(auction))
                .currentPrice(auction.getCurrentPrice())
                .startPrice(auction.getStartPrice())
                .totalBidCount(auction.getTotalBidCount())
                .status(auction.getStatus())
                .scheduledEndTime(auction.getScheduledEndTime())
                .build();
    }

    /**
     * 썸네일 이미지 URL 추출 (첫 번째 이미지)
     */
    private static String extractThumbnail(Auction auction) {
        if (auction.getImageUrls() == null || auction.getImageUrls().isEmpty()) {
            return null;
        }
        return auction.getImageUrls().get(0);
    }
}
