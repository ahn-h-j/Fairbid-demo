package com.cos.fairbid.trade.application.port.in;

import java.util.List;
import java.util.Optional;

import com.cos.fairbid.trade.domain.Trade;

/**
 * 거래 조회 유스케이스
 * 거래 정보 조회 관련 인바운드 포트
 */
public interface TradeQueryUseCase {

    /**
     * 거래 ID로 거래를 조회한다
     *
     * @param tradeId 거래 ID
     * @return 거래 도메인 객체 (Optional)
     */
    Optional<Trade> findById(Long tradeId);

    /**
     * 거래 ID로 거래를 조회한다 (필수)
     *
     * @param tradeId 거래 ID
     * @return 거래 도메인 객체
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     */
    Trade getById(Long tradeId);

    /**
     * 경매 ID로 거래를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 거래 도메인 객체 (Optional)
     */
    Optional<Trade> findByAuctionId(Long auctionId);

    /**
     * 사용자의 거래 목록을 조회한다
     *
     * @param userId 사용자 ID
     * @return 거래 목록 (판매자 또는 구매자로 참여한 거래)
     */
    List<Trade> findByUserId(Long userId);
}
