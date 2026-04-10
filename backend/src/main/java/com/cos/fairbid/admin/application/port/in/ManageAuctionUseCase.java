package com.cos.fairbid.admin.application.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cos.fairbid.admin.application.dto.AdminAuctionResult;
import com.cos.fairbid.auction.domain.AuctionStatus;

/**
 * 관리자 경매 관리 UseCase
 * 관리자가 경매를 조회하고 관리한다.
 */
public interface ManageAuctionUseCase {

    /**
     * 관리자용 경매 목록 조회
     * 판매자 정보를 포함한다.
     *
     * @param status   상태 필터 (optional)
     * @param keyword  검색어 (optional)
     * @param pageable 페이지 정보
     * @return 경매 목록
     */
    Page<AdminAuctionResult> getAuctionList(AuctionStatus status, String keyword, Pageable pageable);
}
