package com.cos.fairbid.trade.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.trade.adapter.out.persistence.mapper.TradeMapper;
import com.cos.fairbid.trade.adapter.out.persistence.repository.TradeJpaRepository;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeStatus;

/**
 * 거래 영속성 어댑터
 * TradeRepositoryPort 구현
 */
@Repository
@RequiredArgsConstructor
public class TradePersistenceAdapter implements TradeRepositoryPort {

    private final TradeJpaRepository tradeJpaRepository;
    private final TradeMapper tradeMapper;

    // 노쇼 체크 대상 상태
    private static final List<TradeStatus> PENDING_STATUSES = List.of(
            TradeStatus.AWAITING_METHOD_SELECTION,
            TradeStatus.AWAITING_ARRANGEMENT
    );

    @Override
    public Trade save(Trade trade) {
        var entity = tradeMapper.toEntity(trade);
        var saved = tradeJpaRepository.save(entity);
        return tradeMapper.toDomain(saved);
    }

    @Override
    public Optional<Trade> findById(Long id) {
        return tradeJpaRepository.findById(id)
                .map(tradeMapper::toDomain);
    }

    @Override
    public Optional<Trade> findByAuctionId(Long auctionId) {
        return tradeJpaRepository.findByAuctionId(auctionId)
                .map(tradeMapper::toDomain);
    }

    @Override
    public List<Trade> findByUserId(Long userId) {
        return tradeJpaRepository.findByUserId(userId).stream()
                .map(tradeMapper::toDomain)
                .toList();
    }

    @Override
    public List<Trade> findReminderTargets() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderTime = now.plusHours(12);
        return tradeJpaRepository.findReminderTargets(PENDING_STATUSES, now, reminderTime).stream()
                .map(tradeMapper::toDomain)
                .toList();
    }

    @Override
    public int countCompletedSales(Long userId) {
        return tradeJpaRepository.countCompletedSales(userId);
    }

    @Override
    public int countCompletedPurchases(Long userId) {
        return tradeJpaRepository.countCompletedPurchases(userId);
    }

    @Override
    public long sumCompletedAmount(Long userId) {
        return tradeJpaRepository.sumCompletedAmount(userId);
    }

    @Override
    public long sumCompletedSalesAmount(Long userId) {
        return tradeJpaRepository.sumCompletedSalesAmount(userId);
    }

    @Override
    public long sumCompletedPurchaseAmount(Long userId) {
        return tradeJpaRepository.sumCompletedPurchaseAmount(userId);
    }
}
