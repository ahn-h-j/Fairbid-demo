package com.cos.fairbid.user.adapter.in.dto;

import java.time.LocalDateTime;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.user.application.port.in.GetMyAuctionsUseCase.MyAuctionItem;

/**
 * 내 판매 경매 응답 DTO
 *
 * @param id           경매 ID
 * @param title        제목
 * @param currentPrice 현재가
 * @param status       경매 상태
 * @param createdAt    등록일
 */
public record MyAuctionResponse(
        Long id,
        String title,
        Long currentPrice,
        AuctionStatus status,
        LocalDateTime createdAt
) {
    /**
     * UseCase 결과에서 응답 DTO를 생성한다.
     */
    public static MyAuctionResponse from(MyAuctionItem item) {
        return new MyAuctionResponse(
                item.id(),
                item.title(),
                item.currentPrice(),
                item.status(),
                item.createdAt()
        );
    }
}
