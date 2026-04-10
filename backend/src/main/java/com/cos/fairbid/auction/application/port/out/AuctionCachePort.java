package com.cos.fairbid.auction.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.TopBidderInfo;

/**
 * 경매 캐시 아웃바운드 포트
 * Redis 캐시 인터페이스
 */
public interface AuctionCachePort {

    /**
     * 경매 정보를 캐시에 저장한다
     *
     * @param auction 경매 도메인 객체
     */
    void saveToCache(Auction auction);

    /**
     * 캐시에서 경매 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 경매 도메인 객체 (캐시 미스 시 빈 Optional)
     */
    Optional<Auction> findById(Long auctionId);

    /**
     * 캐시에서 여러 경매의 실시간 가격 정보를 조회한다 (배치)
     * 경매 목록 페이지에서 Redis의 최신 currentPrice를 표시하기 위해 사용
     *
     * @param auctionIds 경매 ID 목록
     * @return 경매 ID → currentPrice 맵 (캐시 미스인 경매는 맵에 포함되지 않음)
     */
    Map<Long, Long> getCurrentPrices(Set<Long> auctionIds);

    /**
     * 캐시에 경매 정보가 존재하는지 확인한다
     *
     * @param auctionId 경매 ID
     * @return 존재 여부
     */
    boolean existsInCache(Long auctionId);

    // ============================
    // 종료 대기 큐 (Sorted Set) 관련
    // ============================

    /**
     * 경매를 종료 대기 큐에 추가한다 (이미 존재하면 score 갱신)
     * Key: auction:closing, Score: 종료시간(ms), Member: 경매ID
     *
     * @param auctionId  경매 ID
     * @param endTimeMs  종료 예정 시간 (밀리초)
     */
    void addToClosingQueue(Long auctionId, long endTimeMs);

    /**
     * 종료 대기 큐에서 경매를 제거한다
     *
     * @param auctionId 경매 ID
     */
    void removeFromClosingQueue(Long auctionId);

    /**
     * 종료 시간이 지난 경매 ID 목록을 조회한다
     * ZRANGEBYSCORE auction:closing 0 {currentTimeMs}
     *
     * @param currentTimeMs 현재 시간 (밀리초)
     * @return 종료 대상 경매 ID 목록
     */
    List<Long> findAuctionIdsToClose(long currentTimeMs);

    // ============================
    // 캐시 상태 업데이트
    // ============================

    /**
     * 캐시에서 경매 상태를 업데이트한다
     * 종료 처리 시 Redis와 RDB 상태를 동기화하기 위해 사용
     *
     * @param auctionId 경매 ID
     * @param status    변경할 상태
     */
    void updateStatus(Long auctionId, AuctionStatus status);

    // ============================
    // 낙찰자 정보 조회 (Redis 기준)
    // ============================

    /**
     * Redis에서 1순위 입찰자 정보를 조회한다
     * 경매 종료 시 낙찰자 결정에 사용
     *
     * @param auctionId 경매 ID
     * @return 1순위 입찰자 정보 (입찰이 없으면 빈 Optional)
     */
    Optional<TopBidderInfo> getTopBidderInfo(Long auctionId);

    /**
     * Redis에서 2순위 입찰자 정보를 조회한다
     * 1순위 노쇼 시 2순위 승계에 사용
     *
     * @param auctionId 경매 ID
     * @return 2순위 입찰자 정보 (없으면 빈 Optional)
     */
    Optional<TopBidderInfo> getSecondBidderInfo(Long auctionId);
}
