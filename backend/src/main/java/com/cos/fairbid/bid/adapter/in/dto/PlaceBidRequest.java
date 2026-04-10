package com.cos.fairbid.bid.adapter.in.dto;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.cos.fairbid.bid.domain.BidType;

/**
 * 입찰 요청 DTO
 */
@Builder
public record PlaceBidRequest(
        /**
         * 입찰 금액
         * - ONE_TOUCH 입찰 시: 무시됨 (자동으로 최소 입찰가 적용)
         * - DIRECT 입찰 시: 필수
         */
        @Positive(message = "입찰 금액은 0보다 커야 합니다")
        Long amount,

        /**
         * 입찰 유형
         * - ONE_TOUCH: 원터치 입찰 (현재가 + 입찰단위)
         * - DIRECT: 금액 직접 지정
         */
        @NotNull(message = "입찰 유형은 필수입니다")
        BidType bidType
) {
    /**
     * Request DTO → Command 변환
     *
     * @param auctionId 경매 ID (Path Variable)
     * @param bidderId  입찰자 ID (인증 정보에서 추출)
     * @return PlaceBidCommand
     */
    public PlaceBidCommand toCommand(Long auctionId, Long bidderId) {
        return PlaceBidCommand.builder()
                .auctionId(Objects.requireNonNull(auctionId, "auctionId must not be null"))
                .bidderId(Objects.requireNonNull(bidderId, "bidderId must not be null"))
                .amount(amount)
                .bidType(bidType)
                .build();
    }
}
