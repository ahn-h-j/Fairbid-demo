package com.cos.fairbid.trade.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import com.cos.fairbid.trade.adapter.out.persistence.entity.DeliveryInfoEntity;
import com.cos.fairbid.trade.domain.DeliveryInfo;

/**
 * 택배 배송 정보 Entity ↔ Domain 변환 Mapper
 */
@Component
public class DeliveryInfoMapper {

    /**
     * Domain → Entity 변환
     */
    public DeliveryInfoEntity toEntity(DeliveryInfo info) {
        return DeliveryInfoEntity.builder()
                .id(info.getId())
                .tradeId(info.getTradeId())
                .recipientName(info.getRecipientName())
                .recipientPhone(info.getRecipientPhone())
                .postalCode(info.getPostalCode())
                .address(info.getAddress())
                .addressDetail(info.getAddressDetail())
                .courierCompany(info.getCourierCompany())
                .trackingNumber(info.getTrackingNumber())
                .status(info.getStatus())
                .paymentConfirmed(info.isPaymentConfirmed())
                .paymentVerified(info.isPaymentVerified())
                .build();
    }

    /**
     * Entity → Domain 변환
     */
    public DeliveryInfo toDomain(DeliveryInfoEntity entity) {
        return DeliveryInfo.reconstitute()
                .id(entity.getId())
                .tradeId(entity.getTradeId())
                .recipientName(entity.getRecipientName())
                .recipientPhone(entity.getRecipientPhone())
                .postalCode(entity.getPostalCode())
                .address(entity.getAddress())
                .addressDetail(entity.getAddressDetail())
                .courierCompany(entity.getCourierCompany())
                .trackingNumber(entity.getTrackingNumber())
                .status(entity.getStatus())
                .paymentConfirmed(entity.isPaymentConfirmed())
                .paymentVerified(entity.isPaymentVerified())
                .build();
    }
}
