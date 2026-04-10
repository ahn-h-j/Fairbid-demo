package com.cos.fairbid.auction.application.port.out;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;

/**
 * 경매 저장소 아웃바운드 포트
 * 영속성 계층과의 통신을 위한 인터페이스
 */
public interface AuctionRepositoryPort {

    /**
     * 경매를 저장한다
     *
     * @param auction 저장할 경매 도메인 객체
     * @return 저장된 경매 (ID 포함)
     */
    Auction save(Auction auction);

    /**
     * ID로 경매를 조회한다
     *
     * @param id 경매 ID
     * @return 경매 도메인 객체 (Optional)
     */
    Optional<Auction> findById(Long id);

    /**
     * 종료 시간이 도래한 진행 중인 경매 목록을 조회한다
     * status = BIDDING 이고 scheduledEndTime <= now
     *
     * @return 종료 대상 경매 목록
     */
    List<Auction> findClosingAuctions();

    /**
     * 검색 조건에 따라 경매 목록을 페이지네이션하여 조회한다
     *
     * @param status   경매 상태 필터 (nullable)
     * @param category 카테고리 필터 (nullable)
     * @param keyword  검색어 - 상품명 (nullable)
     * @param pageable 페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    Page<Auction> findAll(AuctionStatus status, Category category, String keyword, Pageable pageable);

    /**
     * 경매의 현재가, 입찰수, 입찰단위를 직접 업데이트한다
     * Lua 스크립트 입찰 처리 후 DB 동기화용
     *
     * @param auctionId      경매 ID
     * @param currentPrice   새 현재가
     * @param totalBidCount  새 총 입찰수
     * @param bidIncrement   새 입찰 단위
     */
    void updateCurrentPrice(Long auctionId, Long currentPrice, Integer totalBidCount, Long bidIncrement);

    /**
     * 즉시 구매 활성화 상태로 업데이트한다
     * Lua 스크립트 즉시 구매 처리 후 DB 동기화용
     *
     * @param auctionId                경매 ID
     * @param currentPrice             새 현재가 (즉시구매가)
     * @param totalBidCount            새 총 입찰수
     * @param bidIncrement             새 입찰 단위
     * @param instantBuyerId           즉시 구매 요청자 ID
     * @param instantBuyActivatedTimeMs 즉시 구매 활성화 시간 (밀리초)
     * @param scheduledEndTimeMs       새 종료 예정 시간 (밀리초)
     */
    void updateInstantBuyActivated(
            Long auctionId,
            Long currentPrice,
            Integer totalBidCount,
            Long bidIncrement,
            Long instantBuyerId,
            Long instantBuyActivatedTimeMs,
            Long scheduledEndTimeMs
    );
}
