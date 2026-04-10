package com.cos.fairbid.auction.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;

/**
 * 경매 목록 조회 유스케이스 인터페이스
 */
public interface GetAuctionListUseCase {

    /**
     * 경매 목록을 조회한다
     *
     * @param status   경매 상태 필터 (nullable)
     * @param category 카테고리 필터 (nullable)
     * @param keyword  검색어 - 상품명 (nullable)
     * @param pageable 페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    Page<Auction> getAuctionList(AuctionStatus status, Category category, String keyword, Pageable pageable);
}
