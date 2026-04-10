package com.cos.fairbid.trade.application.port.out;

import java.util.Optional;

import com.cos.fairbid.trade.domain.DeliveryInfo;

/**
 * 택배 배송 정보 저장소 아웃바운드 포트
 */
public interface DeliveryInfoRepositoryPort {

    /**
     * 배송 정보를 저장한다
     */
    DeliveryInfo save(DeliveryInfo deliveryInfo);

    /**
     * 거래 ID로 배송 정보를 조회한다
     */
    Optional<DeliveryInfo> findByTradeId(Long tradeId);

    /**
     * ID로 배송 정보를 조회한다
     */
    Optional<DeliveryInfo> findById(Long id);
}
