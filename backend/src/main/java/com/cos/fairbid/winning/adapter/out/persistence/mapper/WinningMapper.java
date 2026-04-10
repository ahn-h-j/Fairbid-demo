package com.cos.fairbid.winning.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import com.cos.fairbid.winning.adapter.out.persistence.entity.WinningEntity;
import com.cos.fairbid.winning.domain.Winning;

/**
 * Winning Entity ↔ Domain 변환 매퍼
 */
@Component
public class WinningMapper {

    /**
     * Domain → Entity 변환
     *
     * @param winning 도메인 객체
     * @return JPA 엔티티
     */
    public WinningEntity toEntity(Winning winning) {
        return WinningEntity.builder()
                .id(winning.getId())
                .auctionId(winning.getAuctionId())
                .rank(winning.getRank())
                .bidderId(winning.getBidderId())
                .bidAmount(winning.getBidAmount())
                .status(winning.getStatus())
                .responseDeadline(winning.getResponseDeadline())
                .build();
    }

    /**
     * Entity → Domain 변환
     *
     * @param entity JPA 엔티티
     * @return 도메인 객체
     */
    public Winning toDomain(WinningEntity entity) {
        return Winning.reconstitute()
                .id(entity.getId())
                .auctionId(entity.getAuctionId())
                .rank(entity.getRank())
                .bidderId(entity.getBidderId())
                .bidAmount(entity.getBidAmount())
                .status(entity.getStatus())
                .responseDeadline(entity.getResponseDeadline())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
