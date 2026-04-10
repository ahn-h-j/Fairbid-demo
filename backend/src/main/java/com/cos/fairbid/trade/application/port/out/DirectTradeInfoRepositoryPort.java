package com.cos.fairbid.trade.application.port.out;

import java.util.Optional;

import com.cos.fairbid.trade.domain.DirectTradeInfo;

/**
 * 직거래 정보 저장소 아웃바운드 포트
 */
public interface DirectTradeInfoRepositoryPort {

    /**
     * 직거래 정보를 저장한다
     */
    DirectTradeInfo save(DirectTradeInfo directTradeInfo);

    /**
     * 거래 ID로 직거래 정보를 조회한다
     */
    Optional<DirectTradeInfo> findByTradeId(Long tradeId);

    /**
     * ID로 직거래 정보를 조회한다
     */
    Optional<DirectTradeInfo> findById(Long id);
}
