package com.cos.fairbid.bid.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.auction.domain.Auction;

/**
 * 입찰 도메인 모델
 * 입찰 이력을 저장하는 역할
 */
@Getter
@Builder
public class Bid {

    private Long id;
    private Long auctionId;
    private Long bidderId;
    private Long amount;
    private BidType bidType;
    private LocalDateTime createdAt;

    /**
     * 새로운 입찰 생성을 위한 정적 팩토리 메서드
     *
     * @param auctionId 경매 ID
     * @param bidderId  입찰자 ID
     * @param amount    입찰 금액
     * @param bidType   입찰 유형
     * @return 생성된 Bid 도메인 객체
     */
    public static Bid create(Long auctionId, Long bidderId, Long amount, BidType bidType) {
        return Bid.builder()
                .auctionId(auctionId)
                .bidderId(bidderId)
                .amount(amount)
                .bidType(bidType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     * Mapper에서 사용
     */
    public static BidBuilder reconstitute() {
        return Bid.builder();
    }

    /**
     * 입찰 금액을 결정한다
     * BidType 전략 패턴에 위임
     *
     * @param bidType         입찰 유형
     * @param requestedAmount 요청 입찰 금액 (DIRECT 시 필수)
     * @param auction         경매 도메인
     * @return 입찰 금액
     * @see BidType#calculateAmount(Long, Auction)
     */
    public static Long determineBidAmount(BidType bidType, Long requestedAmount, Auction auction) {
        return bidType.calculateAmount(requestedAmount, auction);
    }
}
