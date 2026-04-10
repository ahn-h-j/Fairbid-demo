package com.cos.fairbid.user.application.port.in;

import java.time.LocalDateTime;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.common.pagination.CursorPage;

/**
 * 내 입찰 경매 목록 조회 유스케이스
 * 입찰한 경매별로 내 최고 입찰가와 현재가를 보여준다.
 * 커서 기반 무한스크롤 페이지네이션을 지원한다.
 */
public interface GetMyBidsUseCase {

    /**
     * 내 입찰 경매 목록을 조회한다.
     *
     * @param userId 사용자 ID (bidderId)
     * @param cursor 마지막으로 조회한 경매 ID (null이면 처음부터)
     * @param size   페이지 크기
     * @return 커서 기반 페이지 결과
     */
    CursorPage<MyBidItem> getMyBids(Long userId, Long cursor, int size);

    /**
     * 내 입찰 경매 요약 정보
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
    record MyBidItem(
            Long auctionId,
            String title,
            Long myHighestBid,
            Long currentPrice,
            AuctionStatus status,
            LocalDateTime createdAt,
            Integer winnerRank,
            String winningStatus
    ) {
    }
}
