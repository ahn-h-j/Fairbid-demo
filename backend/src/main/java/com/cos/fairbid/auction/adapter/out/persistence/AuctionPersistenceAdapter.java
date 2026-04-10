package com.cos.fairbid.auction.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.adapter.out.persistence.mapper.AuctionMapper;
import com.cos.fairbid.auction.adapter.out.persistence.repository.AuctionSpecification;
import com.cos.fairbid.auction.adapter.out.persistence.repository.JpaAuctionRepository;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;

/**
 * 경매 영속성 어댑터
 * AuctionRepositoryPort 포트 구현체
 */
@Repository
@RequiredArgsConstructor
public class AuctionPersistenceAdapter implements AuctionRepositoryPort {

    private final JpaAuctionRepository jpaAuctionRepository;
    private final AuctionMapper auctionMapper;

    @Override
    public Auction save(Auction auction) {
        AuctionEntity entity = auctionMapper.toEntity(auction);
        AuctionEntity savedEntity = jpaAuctionRepository.save(entity);
        return auctionMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Auction> findById(Long id) {
        return jpaAuctionRepository.findById(id)
                .map(auctionMapper::toDomain);
    }

    @Override
    public List<Auction> findClosingAuctions() {
        // BIDDING과 INSTANT_BUY_PENDING 상태 모두 종료 대상
        List<AuctionStatus> closingStatuses = List.of(
                AuctionStatus.BIDDING,
                AuctionStatus.INSTANT_BUY_PENDING
        );

        return jpaAuctionRepository.findClosingAuctions(closingStatuses, LocalDateTime.now())
                .stream()
                .map(auctionMapper::toDomain)
                .toList();
    }

    @Override
    public Page<Auction> findAll(AuctionStatus status, Category category, String keyword, Pageable pageable) {
        return jpaAuctionRepository.findAll(
                AuctionSpecification.withCondition(status, category, keyword),
                pageable
        ).map(auctionMapper::toDomain);
    }

    @Override
    public void updateCurrentPrice(Long auctionId, Long currentPrice, Integer totalBidCount, Long bidIncrement) {
        jpaAuctionRepository.updateCurrentPrice(auctionId, currentPrice, totalBidCount, bidIncrement);
    }

    @Override
    public void updateInstantBuyActivated(
            Long auctionId,
            Long currentPrice,
            Integer totalBidCount,
            Long bidIncrement,
            Long instantBuyerId,
            Long instantBuyActivatedTimeMs,
            Long scheduledEndTimeMs
    ) {
        // 밀리초를 LocalDateTime으로 변환
        LocalDateTime instantBuyActivatedTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(instantBuyActivatedTimeMs),
                java.time.ZoneId.systemDefault()
        );
        LocalDateTime scheduledEndTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(scheduledEndTimeMs),
                java.time.ZoneId.systemDefault()
        );

        jpaAuctionRepository.updateInstantBuyActivated(
                auctionId,
                currentPrice,
                totalBidCount,
                bidIncrement,
                instantBuyerId,
                instantBuyActivatedTime,
                scheduledEndTime
        );
    }
}
