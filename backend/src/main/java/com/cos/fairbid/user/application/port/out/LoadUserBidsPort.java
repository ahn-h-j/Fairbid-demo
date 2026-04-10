package com.cos.fairbid.user.application.port.out;

import java.util.List;

import com.cos.fairbid.user.application.port.in.GetMyBidsUseCase.MyBidItem;

/**
 * 사용자의 입찰 경매 목록 조회 포트
 * user 모듈에서 bid + auction 데이터를 조회하기 위한 아웃바운드 인터페이스
 */
public interface LoadUserBidsPort {

    /**
     * 입찰자 ID로 입찰한 경매 목록을 커서 기반으로 조회한다.
     * 각 경매에 대한 내 최고 입찰가와 현재가를 포함한다.
     *
     * @param bidderId 입찰자 ID
     * @param cursor   커서 (이전 페이지의 마지막 경매 ID, null이면 처음부터)
     * @param limit    조회 개수
     * @return 입찰 경매 요약 목록
     */
    List<MyBidItem> findBidAuctionsByBidderWithCursor(Long bidderId, Long cursor, int limit);
}
