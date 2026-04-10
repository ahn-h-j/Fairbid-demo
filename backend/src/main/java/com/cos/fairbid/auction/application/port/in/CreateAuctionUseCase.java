package com.cos.fairbid.auction.application.port.in;

import java.util.List;

import lombok.Builder;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionDuration;
import com.cos.fairbid.auction.domain.Category;

/**
 * 경매 생성 유스케이스 인터페이스
 */
public interface CreateAuctionUseCase {

    /**
     * 새로운 경매를 생성한다
     *
     * @param command 경매 생성 명령
     * @return 생성된 경매 도메인 객체
     */
    Auction createAuction(CreateAuctionCommand command);

    /**
     * 경매 생성 명령 객체
     */
    @Builder
    record CreateAuctionCommand(
            Long sellerId,
            String title,
            String description,
            Category category,
            Long startPrice,
            Long instantBuyPrice,
            AuctionDuration duration,
            List<String> imageUrls,
            Boolean directTradeAvailable,   // 직거래 가능 여부
            Boolean deliveryAvailable,      // 택배 가능 여부
            String directTradeLocation      // 직거래 희망 위치
    ) {
    }
}
