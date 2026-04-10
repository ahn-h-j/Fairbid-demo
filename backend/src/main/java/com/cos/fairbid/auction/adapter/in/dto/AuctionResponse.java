package com.cos.fairbid.auction.adapter.in.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;

/**
 * 경매 응답 DTO
 * 기본 경매 정보 + 계산된 비즈니스 필드 포함
 */
@Builder
public record AuctionResponse(
        // 기본 정보
        Long id,
        Long sellerId,
        String title,
        String description,
        Category category,
        Long startPrice,
        Long currentPrice,
        Long instantBuyPrice,
        Long bidIncrement,
        LocalDateTime scheduledEndTime,
        Integer extensionCount,
        Integer totalBidCount,
        AuctionStatus status,
        List<String> imageUrls,
        LocalDateTime createdAt,

        // 계산된 비즈니스 필드
        boolean instantBuyEnabled,  // 즉시 구매 버튼 활성화 여부
        Long nextMinBidPrice,       // 다음 입찰 가능 최소 금액
        boolean editable,           // 수정 가능 여부

        // 거래 방식 정보
        Boolean directTradeAvailable,   // 직거래 가능 여부
        Boolean deliveryAvailable,      // 택배 가능 여부
        String directTradeLocation,     // 직거래 희망 위치

        // 낙찰 정보 (종료된 경매)
        Long winnerId,                  // 낙찰자 ID
        Long finalPrice,                // 최종 낙찰가 (종료 시 currentPrice)

        // 현재 사용자의 낙찰 순위 (1: 1순위 낙찰자, 2: 2순위 낙찰자, null: 낙찰자 아님)
        Integer userWinningRank,

        // 현재 사용자의 낙찰 상태 (PENDING_RESPONSE, RESPONDED, NO_SHOW, STANDBY, FAILED)
        String userWinningStatus,

        // 현재 사용자의 입찰 순위 (진행 중인 경매에서 1: 1순위, null: 1순위 아님)
        Integer userBidRank
) {
    /**
     * Domain → Response DTO 변환 (기본)
     */
    public static AuctionResponse from(Auction auction) {
        return from(auction, null, null, null);
    }

    /**
     * Domain → Response DTO 변환 (사용자 낙찰 정보 포함)
     *
     * @param auction           경매 도메인 객체
     * @param userWinningRank   현재 사용자의 낙찰 순위 (1, 2, 또는 null)
     * @param userWinningStatus 현재 사용자의 낙찰 상태
     * @return 경매 응답 DTO
     */
    public static AuctionResponse from(Auction auction, Integer userWinningRank, String userWinningStatus) {
        return from(auction, userWinningRank, userWinningStatus, null);
    }

    /**
     * Domain → Response DTO 변환 (사용자 낙찰 정보 + 입찰 순위 포함)
     *
     * @param auction           경매 도메인 객체
     * @param userWinningRank   현재 사용자의 낙찰 순위 (1, 2, 또는 null)
     * @param userWinningStatus 현재 사용자의 낙찰 상태
     * @param userBidRank       현재 사용자의 입찰 순위 (진행 중 경매에서 1, 2, 또는 null)
     * @return 경매 응답 DTO
     */
    public static AuctionResponse from(
            Auction auction, Integer userWinningRank,
            String userWinningStatus, Integer userBidRank) {
        return AuctionResponse.builder()
                // 기본 정보
                .id(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .category(auction.getCategory())
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .instantBuyPrice(auction.getInstantBuyPrice())
                .bidIncrement(auction.getBidIncrement())
                .scheduledEndTime(auction.getScheduledEndTime())
                .extensionCount(auction.getExtensionCount())
                .totalBidCount(auction.getTotalBidCount())
                .status(auction.getStatus())
                .imageUrls(auction.getImageUrls())
                .createdAt(auction.getCreatedAt())
                // 계산된 비즈니스 필드
                .instantBuyEnabled(auction.isInstantBuyEnabled())
                .nextMinBidPrice(auction.getNextMinBidPrice())
                .editable(auction.isEditable())
                // 거래 방식 정보
                .directTradeAvailable(auction.getDirectTradeAvailable())
                .deliveryAvailable(auction.getDeliveryAvailable())
                .directTradeLocation(auction.getDirectTradeLocation())
                // 낙찰 정보
                .winnerId(auction.getWinnerId())
                .finalPrice(auction.getWinnerId() != null ? auction.getCurrentPrice() : null)
                // 사용자 낙찰 순위 및 상태
                .userWinningRank(userWinningRank)
                .userWinningStatus(userWinningStatus)
                // 사용자 입찰 순위 (진행 중 경매)
                .userBidRank(userBidRank)
                .build();
    }
}
