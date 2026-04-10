package com.cos.fairbid.user.adapter.in.dto;

import java.time.LocalDateTime;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.user.application.port.in.GetMyBidsUseCase.MyBidItem;

/**
 * 내 입찰 경매 응답 DTO
 *
 * @param auctionId     경매 ID
 * @param title         제목
 * @param myHighestBid  내 최고 입찰가
 * @param currentPrice  현재가
 * @param status        경매 상태
 * @param createdAt     등록일
 * @param winnerRank    낙찰 순위 (1: 1순위, 2: 2순위, null: 미낙찰/진행중)
 * @param winningStatus Winning 상태 (PENDING_RESPONSE, RESPONDED, NO_SHOW, FAILED, STANDBY)
 */
public record MyBidResponse(
        Long auctionId,
        String title,
        Long myHighestBid,
        Long currentPrice,
        AuctionStatus status,
        LocalDateTime createdAt,
        Integer winnerRank,
        String winningStatus
) {
    /**
     * UseCase 결과에서 응답 DTO를 생성한다.
     */
    public static MyBidResponse from(MyBidItem item) {
        return new MyBidResponse(
                item.auctionId(),
                item.title(),
                item.myHighestBid(),
                item.currentPrice(),
                item.status(),
                item.createdAt(),
                item.winnerRank(),
                item.winningStatus()
        );
    }
}
