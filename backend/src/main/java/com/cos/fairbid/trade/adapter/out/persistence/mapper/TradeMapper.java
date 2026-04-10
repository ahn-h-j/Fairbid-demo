package com.cos.fairbid.trade.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import com.cos.fairbid.trade.adapter.out.persistence.entity.TradeEntity;
import com.cos.fairbid.trade.domain.Trade;

/**
 * 거래 Entity ↔ Domain 변환 Mapper
 */
@Component
public class TradeMapper {

    /**
     * Domain → Entity 변환
     */
    public TradeEntity toEntity(Trade trade) {
        return TradeEntity.builder()
                .id(trade.getId())
                .auctionId(trade.getAuctionId())
                .sellerId(trade.getSellerId())
                .buyerId(trade.getBuyerId())
                .finalPrice(trade.getFinalPrice())
                .status(trade.getStatus())
                .method(trade.getMethod())
                .responseDeadline(trade.getResponseDeadline())
                .reminderSentAt(trade.getReminderSentAt())
                .createdAt(trade.getCreatedAt())
                .completedAt(trade.getCompletedAt())
                .build();
    }

    /**
     * Entity → Domain 변환
     */
    public Trade toDomain(TradeEntity entity) {
        return Trade.reconstitute()
                .id(entity.getId())
                .auctionId(entity.getAuctionId())
                .sellerId(entity.getSellerId())
                .buyerId(entity.getBuyerId())
                .finalPrice(entity.getFinalPrice())
                .status(entity.getStatus())
                .method(entity.getMethod())
                .responseDeadline(entity.getResponseDeadline())
                .reminderSentAt(entity.getReminderSentAt())
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
