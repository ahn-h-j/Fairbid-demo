package com.cos.fairbid.admin.adapter.in.dto;

import java.time.LocalDateTime;

import lombok.Builder;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;

/**
 * 관리자용 경매 목록 응답 DTO
 * 판매자 정보를 포함한다.
 */
@Builder
public record AdminAuctionResponse(
        Long id,
        String title,
        String thumbnailUrl,
        Long currentPrice,
        Long startPrice,
        Integer totalBidCount,
        AuctionStatus status,
        LocalDateTime scheduledEndTime,
        LocalDateTime createdAt,
        Integer extensionCount,
        // 판매자 정보
        Long sellerId,
        String sellerNickname
) {
    /**
     * Domain → Response DTO 변환
     *
     * @param auction        경매 도메인 객체
     * @param sellerNickname 판매자 닉네임
     * @return 관리자용 경매 응답 DTO
     */
    public static AdminAuctionResponse from(Auction auction, String sellerNickname) {
        return AdminAuctionResponse.builder()
                .id(auction.getId())
                .title(auction.getTitle())
                .thumbnailUrl(extractThumbnail(auction))
                .currentPrice(auction.getCurrentPrice())
                .startPrice(auction.getStartPrice())
                .totalBidCount(auction.getTotalBidCount())
                .status(auction.getStatus())
                .scheduledEndTime(auction.getScheduledEndTime())
                .createdAt(auction.getCreatedAt())
                .extensionCount(auction.getExtensionCount())
                .sellerId(auction.getSellerId())
                .sellerNickname(sellerNickname)
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
