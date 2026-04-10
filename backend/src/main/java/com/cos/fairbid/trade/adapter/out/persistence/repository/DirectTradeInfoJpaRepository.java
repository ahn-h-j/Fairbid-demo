package com.cos.fairbid.trade.adapter.out.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cos.fairbid.trade.adapter.out.persistence.entity.DirectTradeInfoEntity;

/**
 * 직거래 정보 JPA Repository
 */
public interface DirectTradeInfoJpaRepository extends JpaRepository<DirectTradeInfoEntity, Long> {

    /**
     * 거래 ID로 직거래 정보 조회
     */
    Optional<DirectTradeInfoEntity> findByTradeId(Long tradeId);
}
