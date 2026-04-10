package com.cos.fairbid.winning.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cos.fairbid.winning.adapter.out.persistence.entity.WinningEntity;
import com.cos.fairbid.winning.domain.WinningStatus;

/**
 * 낙찰 Spring Data JPA Repository
 */
public interface JpaWinningRepository extends JpaRepository<WinningEntity, Long> {

    /**
     * 경매 ID로 낙찰 정보를 조회한다
     */
    List<WinningEntity> findByAuctionId(Long auctionId);

    /**
     * 경매 ID와 순위로 낙찰 정보를 조회한다
     */
    Optional<WinningEntity> findByAuctionIdAndRank(Long auctionId, Integer rank);

    /**
     * 응답 기한이 만료된 응답 대기 중인 낙찰 목록을 조회한다
     */
    @Query("SELECT w FROM WinningEntity w "
            + "WHERE w.status = :status "
            + "AND w.responseDeadline IS NOT NULL "
            + "AND w.responseDeadline <= :now")
    List<WinningEntity> findExpiredPendingResponses(
            @Param("status") WinningStatus status,
            @Param("now") LocalDateTime now
    );

    /**
     * 경매 ID와 입찰자 ID로 응답 대기 중인 낙찰 정보를 조회한다
     * 거래 조율 시 현재 구매자에 해당하는 PENDING_RESPONSE 상태의 Winning을 찾는다
     */
    @Query("SELECT w FROM WinningEntity w "
            + "WHERE w.auctionId = :auctionId "
            + "AND w.bidderId = :bidderId "
            + "AND w.status = :status")
    Optional<WinningEntity> findByAuctionIdAndBidderIdAndStatus(
            @Param("auctionId") Long auctionId,
            @Param("bidderId") Long bidderId,
            @Param("status") WinningStatus status
    );

    /**
     * 입찰자 ID로 낙찰 정보를 조회한다 (마이페이지용)
     */
    List<WinningEntity> findByBidderId(Long bidderId);
}
