package com.cos.fairbid.bid.adapter.out.persistence.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cos.fairbid.bid.adapter.out.persistence.entity.BidEntity;

/**
 * 입찰 JPA Repository
 * Spring Data JPA 인터페이스
 */
public interface JpaBidRepository extends JpaRepository<BidEntity, Long> {

    /**
     * 경매의 상위 2개 입찰을 조회한다 (금액 내림차순)
     *
     * @param auctionId 경매 ID
     * @return 상위 2개 입찰 엔티티 목록
     */
    @Query("SELECT b FROM BidEntity b WHERE b.auctionId = :auctionId ORDER BY b.amount DESC LIMIT 2")
    List<BidEntity> findTop2ByAuctionIdOrderByAmountDesc(@Param("auctionId") Long auctionId);

    /**
     * 입찰자가 입찰한 경매 목록을 커서 기반으로 조회한다.
     * 각 경매별 내 최고 입찰가를 GROUP BY로 집계한다.
     * 결과: [auctionId, title, myHighestBid, currentPrice, status, createdAt]
     *
     * @param bidderId 입찰자 ID
     * @param cursor   커서 (이전 페이지의 마지막 경매 ID, null이면 처음부터)
     * @param pageable 페이지 크기 지정용
     * @return Object[] 배열 목록 (JPQL projection)
     */
    @Query("SELECT a.id, a.title, MAX(b.amount), a.currentPrice, a.status, a.createdAt "
            + "FROM BidEntity b "
            + "JOIN com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity a "
            + "ON b.auctionId = a.id "
            + "WHERE b.bidderId = :bidderId "
            + "AND (:cursor IS NULL OR a.id < :cursor) "
            + "GROUP BY a.id, a.title, a.currentPrice, a.status, a.createdAt "
            + "ORDER BY a.id DESC")
    List<Object[]> findBidAuctionsByBidderWithCursor(
            @Param("bidderId") Long bidderId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
