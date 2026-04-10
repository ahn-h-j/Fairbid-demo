package com.cos.fairbid.trade.adapter.out.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cos.fairbid.trade.adapter.out.persistence.entity.TradeEntity;
import com.cos.fairbid.trade.domain.TradeStatus;

/**
 * 거래 JPA Repository
 */
public interface TradeJpaRepository extends JpaRepository<TradeEntity, Long> {

    /**
     * 경매 ID로 거래 조회
     */
    Optional<TradeEntity> findByAuctionId(Long auctionId);

    /**
     * 사용자의 거래 목록 조회 (구매자 또는 판매자로 참여한 거래)
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.sellerId = :userId OR t.buyerId = :userId ORDER BY t.createdAt DESC")
    List<TradeEntity> findByUserId(@Param("userId") Long userId);

    /**
     * 리마인더 발송 대상 거래 목록 조회 (기한 12시간 전 ~ 기한 사이, 아직 발송 안된 건)
     */
    @Query("SELECT t FROM TradeEntity t "
            + "WHERE t.status IN :statuses AND t.responseDeadline IS NOT NULL "
            + "AND t.responseDeadline > :now AND t.responseDeadline <= :reminderTime "
            + "AND t.reminderSentAt IS NULL")
    List<TradeEntity> findReminderTargets(
            @Param("statuses") List<TradeStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("reminderTime") LocalDateTime reminderTime
    );

    /**
     * 사용자의 완료된 판매 수를 조회한다
     */
    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.sellerId = :userId AND t.status = 'COMPLETED'")
    int countCompletedSales(@Param("userId") Long userId);

    /**
     * 사용자의 완료된 구매 수를 조회한다
     */
    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.buyerId = :userId AND t.status = 'COMPLETED'")
    int countCompletedPurchases(@Param("userId") Long userId);

    /**
     * 사용자의 총 거래 금액을 조회한다 (판매 + 구매)
     */
    @Query("SELECT COALESCE(SUM(t.finalPrice), 0) FROM TradeEntity t "
            + "WHERE (t.sellerId = :userId OR t.buyerId = :userId) AND t.status = 'COMPLETED'")
    long sumCompletedAmount(@Param("userId") Long userId);

    /**
     * 사용자의 총 판매 금액을 조회한다
     */
    @Query("SELECT COALESCE(SUM(t.finalPrice), 0) FROM TradeEntity t "
            + "WHERE t.sellerId = :userId AND t.status = 'COMPLETED'")
    long sumCompletedSalesAmount(@Param("userId") Long userId);

    /**
     * 사용자의 총 구매 금액을 조회한다
     */
    @Query("SELECT COALESCE(SUM(t.finalPrice), 0) FROM TradeEntity t "
            + "WHERE t.buyerId = :userId AND t.status = 'COMPLETED'")
    long sumCompletedPurchaseAmount(@Param("userId") Long userId);
}
