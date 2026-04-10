package com.cos.fairbid.user.application.port.out;

import java.util.List;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.user.application.port.in.GetMyAuctionsUseCase.MyAuctionItem;

/**
 * 사용자의 판매 경매 목록 조회 포트
 * user 모듈에서 auction 데이터를 조회하기 위한 아웃바운드 인터페이스
 */
public interface LoadUserAuctionsPort {

    /**
     * 판매자 ID로 경매 목록을 커서 기반으로 조회한다.
     *
     * @param sellerId 판매자 ID
     * @param status   필터링할 경매 상태 (null이면 전체)
     * @param cursor   커서 (이전 페이지의 마지막 ID, null이면 처음부터)
     * @param limit    조회 개수
     * @return 경매 요약 목록
     */
    List<MyAuctionItem> findBySellerIdWithCursor(Long sellerId, AuctionStatus status, Long cursor, int limit);
}
