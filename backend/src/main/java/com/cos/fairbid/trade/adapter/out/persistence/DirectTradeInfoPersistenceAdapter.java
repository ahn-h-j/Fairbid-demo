package com.cos.fairbid.trade.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.trade.adapter.out.persistence.mapper.DirectTradeInfoMapper;
import com.cos.fairbid.trade.adapter.out.persistence.repository.DirectTradeInfoJpaRepository;
import com.cos.fairbid.trade.application.port.out.DirectTradeInfoRepositoryPort;
import com.cos.fairbid.trade.domain.DirectTradeInfo;

/**
 * 직거래 정보 영속성 어댑터
 * DirectTradeInfoRepositoryPort 구현
 */
@Repository
@RequiredArgsConstructor
public class DirectTradeInfoPersistenceAdapter implements DirectTradeInfoRepositoryPort {

    private final DirectTradeInfoJpaRepository directTradeInfoJpaRepository;
    private final DirectTradeInfoMapper directTradeInfoMapper;

    @Override
    public DirectTradeInfo save(DirectTradeInfo directTradeInfo) {
        var entity = directTradeInfoMapper.toEntity(directTradeInfo);
        var saved = directTradeInfoJpaRepository.save(entity);
        return directTradeInfoMapper.toDomain(saved);
    }

    @Override
    public Optional<DirectTradeInfo> findByTradeId(Long tradeId) {
        return directTradeInfoJpaRepository.findByTradeId(tradeId)
                .map(directTradeInfoMapper::toDomain);
    }

    @Override
    public Optional<DirectTradeInfo> findById(Long id) {
        return directTradeInfoJpaRepository.findById(id)
                .map(directTradeInfoMapper::toDomain);
    }
}
