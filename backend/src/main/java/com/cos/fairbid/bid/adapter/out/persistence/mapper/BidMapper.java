package com.cos.fairbid.bid.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import com.cos.fairbid.bid.adapter.out.persistence.entity.BidEntity;
import com.cos.fairbid.bid.domain.Bid;

/**
 * 입찰 Entity ↔ Domain 변환 Mapper
 */
@Component
public class BidMapper {

    /**
     * Domain → Entity 변환
     */
    public BidEntity toEntity(Bid bid) {
        return BidEntity.builder()
                .id(bid.getId())
                .auctionId(bid.getAuctionId())
                .bidderId(bid.getBidderId())
                .amount(bid.getAmount())
                .bidType(bid.getBidType())
                .build();
    }

    /**
     * Entity → Domain 변환
     */
    public Bid toDomain(BidEntity entity) {
        return Bid.reconstitute()
                .id(entity.getId())
                .auctionId(entity.getAuctionId())
                .bidderId(entity.getBidderId())
                .amount(entity.getAmount())
                .bidType(entity.getBidType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
