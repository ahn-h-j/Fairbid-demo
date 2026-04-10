package com.cos.fairbid.trade.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.trade.adapter.out.persistence.mapper.DeliveryInfoMapper;
import com.cos.fairbid.trade.adapter.out.persistence.repository.DeliveryInfoJpaRepository;
import com.cos.fairbid.trade.application.port.out.DeliveryInfoRepositoryPort;
import com.cos.fairbid.trade.domain.DeliveryInfo;

/**
 * 택배 배송 정보 영속성 어댑터
 * DeliveryInfoRepositoryPort 구현
 */
@Repository
@RequiredArgsConstructor
public class DeliveryInfoPersistenceAdapter implements DeliveryInfoRepositoryPort {

    private final DeliveryInfoJpaRepository deliveryInfoJpaRepository;
    private final DeliveryInfoMapper deliveryInfoMapper;

    @Override
    public DeliveryInfo save(DeliveryInfo deliveryInfo) {
        var entity = deliveryInfoMapper.toEntity(deliveryInfo);
        var saved = deliveryInfoJpaRepository.save(entity);
        return deliveryInfoMapper.toDomain(saved);
    }

    @Override
    public Optional<DeliveryInfo> findByTradeId(Long tradeId) {
        return deliveryInfoJpaRepository.findByTradeId(tradeId)
                .map(deliveryInfoMapper::toDomain);
    }

    @Override
    public Optional<DeliveryInfo> findById(Long id) {
        return deliveryInfoJpaRepository.findById(id)
                .map(deliveryInfoMapper::toDomain);
    }
}
