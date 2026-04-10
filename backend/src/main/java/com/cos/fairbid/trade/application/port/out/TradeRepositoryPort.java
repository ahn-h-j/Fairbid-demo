package com.cos.fairbid.trade.application.port.out;

import java.util.List;
import java.util.Optional;

import com.cos.fairbid.trade.domain.Trade;

/**
 * 거래 저장소 아웃바운드 포트
 */
public interface TradeRepositoryPort {

    /**
     * 거래를 저장한다
     */
    Trade save(Trade trade);

    /**
     * ID로 거래를 조회한다
     */
    Optional<Trade> findById(Long id);

    /**
     * 경매 ID로 거래를 조회한다
     */
    Optional<Trade> findByAuctionId(Long auctionId);

    /**
     * 사용자의 거래 목록을 조회한다 (구매자 또는 판매자로 참여한 거래)
     */
    List<Trade> findByUserId(Long userId);

    /**
     * 리마인더 발송 대상 거래 목록을 조회한다
     * (응답 기한 12시간 전)
     */
    List<Trade> findReminderTargets();

    /**
     * 사용자의 완료된 판매 수를 조회한다
     */
    int countCompletedSales(Long userId);

    /**
     * 사용자의 완료된 구매 수를 조회한다
     */
    int countCompletedPurchases(Long userId);

    /**
     * 사용자의 총 거래 금액을 조회한다 (판매 + 구매)
     */
    long sumCompletedAmount(Long userId);

    /**
     * 사용자의 총 판매 금액을 조회한다
     */
    long sumCompletedSalesAmount(Long userId);

    /**
     * 사용자의 총 구매 금액을 조회한다
     */
    long sumCompletedPurchaseAmount(Long userId);
}
