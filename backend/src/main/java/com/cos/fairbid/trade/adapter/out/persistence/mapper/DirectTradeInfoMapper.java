package com.cos.fairbid.trade.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import com.cos.fairbid.trade.adapter.out.persistence.entity.DirectTradeInfoEntity;
import com.cos.fairbid.trade.domain.DirectTradeInfo;

/**
 * 직거래 정보 Entity ↔ Domain 변환 Mapper
 */
@Component
public class DirectTradeInfoMapper {

    /**
     * Domain → Entity 변환
     */
    public DirectTradeInfoEntity toEntity(DirectTradeInfo info) {
        return DirectTradeInfoEntity.builder()
                .id(info.getId())
                .tradeId(info.getTradeId())
                .location(info.getLocation())
                .meetingDate(info.getMeetingDate())
                .meetingTime(info.getMeetingTime())
                .status(info.getStatus())
                .proposedBy(info.getProposedBy())
                .build();
    }

    /**
     * Entity → Domain 변환
     */
    public DirectTradeInfo toDomain(DirectTradeInfoEntity entity) {
        return DirectTradeInfo.reconstitute()
                .id(entity.getId())
                .tradeId(entity.getTradeId())
                .location(entity.getLocation())
                .meetingDate(entity.getMeetingDate())
                .meetingTime(entity.getMeetingTime())
                .status(entity.getStatus())
                .proposedBy(entity.getProposedBy())
                .build();
    }
}
