package com.cos.fairbid.user.application.port.in;

import java.time.LocalDateTime;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.common.pagination.CursorPage;

/**
 * 내 판매 경매 목록 조회 유스케이스
 * 커서 기반 무한스크롤 페이지네이션을 지원한다.
 */
public interface GetMyAuctionsUseCase {

    /**
     * 내 판매 경매 목록을 조회한다.
     *
     * @param userId 사용자 ID (sellerId)
     * @param status 필터링할 경매 상태 (null이면 전체)
     * @param cursor 마지막으로 조회한 경매 ID (null이면 처음부터)
     * @param size   페이지 크기
     * @return 커서 기반 페이지 결과
     */
    CursorPage<MyAuctionItem> getMyAuctions(Long userId, AuctionStatus status, Long cursor, int size);

    /**
     * 내 판매 경매 요약 정보
     */
    record MyAuctionItem(
            Long id,
            String title,
            Long currentPrice,
            AuctionStatus status,
            LocalDateTime createdAt
    ) {
    }
}
