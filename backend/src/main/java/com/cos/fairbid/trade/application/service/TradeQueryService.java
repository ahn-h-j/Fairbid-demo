package com.cos.fairbid.trade.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.trade.application.port.in.TradeQueryUseCase;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.exception.TradeNotFoundException;

/**
 * 거래 조회 서비스
 * TradeQueryUseCase 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeQueryService implements TradeQueryUseCase {

    private final TradeRepositoryPort tradeRepositoryPort;

    @Override
    public Optional<Trade> findById(Long tradeId) {
        return tradeRepositoryPort.findById(tradeId);
    }

    @Override
    public Trade getById(Long tradeId) {
        return tradeRepositoryPort.findById(tradeId)
                .orElseThrow(() -> TradeNotFoundException.withId(tradeId));
    }

    @Override
    public Optional<Trade> findByAuctionId(Long auctionId) {
        return tradeRepositoryPort.findByAuctionId(auctionId);
    }

    @Override
    public List<Trade> findByUserId(Long userId) {
        return tradeRepositoryPort.findByUserId(userId);
    }
}
