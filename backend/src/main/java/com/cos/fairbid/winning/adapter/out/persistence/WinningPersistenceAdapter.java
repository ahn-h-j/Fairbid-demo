package com.cos.fairbid.winning.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.winning.adapter.out.persistence.entity.WinningEntity;
import com.cos.fairbid.winning.adapter.out.persistence.mapper.WinningMapper;
import com.cos.fairbid.winning.adapter.out.persistence.repository.JpaWinningRepository;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;
import com.cos.fairbid.winning.domain.WinningStatus;

/**
 * 낙찰 영속성 어댑터
 * WinningRepositoryPort 포트 구현체
 */
@Repository
@RequiredArgsConstructor
public class WinningPersistenceAdapter implements WinningRepositoryPort {

    private final JpaWinningRepository jpaWinningRepository;
    private final WinningMapper winningMapper;

    @Override
    public Winning save(Winning winning) {
        WinningEntity entity = winningMapper.toEntity(winning);
        WinningEntity savedEntity = jpaWinningRepository.save(entity);
        return winningMapper.toDomain(savedEntity);
    }

    @Override
    public List<Winning> findByAuctionId(Long auctionId) {
        return jpaWinningRepository.findByAuctionId(auctionId)
                .stream()
                .map(winningMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Winning> findByAuctionIdAndRank(Long auctionId, Integer rank) {
        return jpaWinningRepository.findByAuctionIdAndRank(auctionId, rank)
                .map(winningMapper::toDomain);
    }

    @Override
    public List<Winning> findExpiredPendingResponses() {
        return jpaWinningRepository.findExpiredPendingResponses(
                        WinningStatus.PENDING_RESPONSE,
                        LocalDateTime.now()
                )
                .stream()
                .map(winningMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Winning> findById(Long id) {
        return jpaWinningRepository.findById(id)
                .map(winningMapper::toDomain);
    }

    @Override
    public Optional<Winning> findPendingByAuctionIdAndBidderId(Long auctionId, Long bidderId) {
        return jpaWinningRepository.findByAuctionIdAndBidderIdAndStatus(
                        auctionId, bidderId, WinningStatus.PENDING_RESPONSE)
                .map(winningMapper::toDomain);
    }
}
