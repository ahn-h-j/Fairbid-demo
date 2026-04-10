package com.cos.fairbid.auction.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.domain.AuctionStatus;

/**
 * 경매 JPA Repository
 * Spring Data JPA 인터페이스
 * JpaSpecificationExecutor: 동적 쿼리 지원
 */
public interface JpaAuctionRepository
        extends JpaRepository<AuctionEntity, Long>, JpaSpecificationExecutor<AuctionEntity> {

    /**
     * 종료 시간이 도래한 진행 중인 경매 목록을 조회한다
     * BIDDING, INSTANT_BUY_PENDING 상태 모두 조회
     *
     * @param statuses 경매 상태 목록 (BIDDING, INSTANT_BUY_PENDING)
     * @param now      현재 시간
     * @return 종료 대상 경매 엔티티 목록
     */
    @Query("SELECT a FROM AuctionEntity a WHERE a.status IN :statuses AND a.scheduledEndTime <= :now")
    List<AuctionEntity> findClosingAuctions(
            @Param("statuses") List<AuctionStatus> statuses,
            @Param("now") LocalDateTime now
    );

    /**
     * 경매의 현재가, 입찰수, 입찰단위를 직접 업데이트한다
     * Lua 스크립트 입찰 처리 후 DB 동기화용
     */
    @Modifying
    @Query("UPDATE AuctionEntity a SET a.currentPrice = :currentPrice, "
            + "a.totalBidCount = :totalBidCount, a.bidIncrement = :bidIncrement "
            + "WHERE a.id = :auctionId")
    void updateCurrentPrice(
            @Param("auctionId") Long auctionId,
            @Param("currentPrice") Long currentPrice,
            @Param("totalBidCount") Integer totalBidCount,
            @Param("bidIncrement") Long bidIncrement
    );

    /**
     * 판매자의 경매 목록을 커서 기반으로 조회한다.
     * 마이페이지 내 판매 목록에서 사용한다.
     *
     * @param sellerId 판매자 ID
     * @param status   필터링할 경매 상태 (null이면 전체)
     * @param cursor   커서 (이전 페이지의 마지막 경매 ID, null이면 처음부터)
     * @param pageable 페이지 크기 지정용
     * @return 경매 엔티티 목록
     */
    @Query("SELECT a FROM AuctionEntity a WHERE a.sellerId = :sellerId "
            + "AND (:status IS NULL OR a.status = :status) "
            + "AND (:cursor IS NULL OR a.id < :cursor) "
            + "ORDER BY a.id DESC")
    List<AuctionEntity> findBySellerIdWithCursor(
            @Param("sellerId") Long sellerId,
            @Param("status") AuctionStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    /**
     * 즉시 구매 활성화 상태로 업데이트한다
     * Lua 스크립트 즉시 구매 처리 후 DB 동기화용
     */
    @Modifying
    @Query("UPDATE AuctionEntity a SET "
            + "a.status = 'INSTANT_BUY_PENDING', "
            + "a.currentPrice = :currentPrice, "
            + "a.totalBidCount = :totalBidCount, "
            + "a.bidIncrement = :bidIncrement, "
            + "a.instantBuyerId = :instantBuyerId, "
            + "a.instantBuyActivatedTime = :instantBuyActivatedTime, "
            + "a.scheduledEndTime = :scheduledEndTime "
            + "WHERE a.id = :auctionId")
    void updateInstantBuyActivated(
            @Param("auctionId") Long auctionId,
            @Param("currentPrice") Long currentPrice,
            @Param("totalBidCount") Integer totalBidCount,
            @Param("bidIncrement") Long bidIncrement,
            @Param("instantBuyerId") Long instantBuyerId,
            @Param("instantBuyActivatedTime") LocalDateTime instantBuyActivatedTime,
            @Param("scheduledEndTime") LocalDateTime scheduledEndTime
    );
}
