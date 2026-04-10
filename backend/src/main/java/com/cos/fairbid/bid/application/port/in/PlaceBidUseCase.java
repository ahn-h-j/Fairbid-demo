package com.cos.fairbid.bid.application.port.in;

import lombok.Builder;

import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.bid.domain.BidType;

/**
 * 입찰 유스케이스 인터페이스
 */
public interface PlaceBidUseCase {

    /**
     * 입찰을 처리한다
     *
     * @param command 입찰 명령
     * @return 생성된 입찰 도메인 객체
     */
    Bid placeBid(PlaceBidCommand command);

    /**
     * 입찰 명령 객체
     *
     * @param auctionId 경매 ID
     * @param bidderId  입찰자 ID
     * @param amount    입찰 금액 (ONE_TOUCH인 경우 무시됨)
     * @param bidType   입찰 유형 (ONE_TOUCH / DIRECT)
     */
    @Builder
    record PlaceBidCommand(
            Long auctionId,
            Long bidderId,
            Long amount,
            BidType bidType
    ) {
    }
}
