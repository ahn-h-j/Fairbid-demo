package com.cos.fairbid.trade.adapter.in.dto;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.trade.domain.DeliveryInfo;
import com.cos.fairbid.trade.domain.DeliveryStatus;

/**
 * 배송 정보 응답 DTO
 */
@Getter
@Builder
public class DeliveryInfoResponse {

    private Long id;
    private Long tradeId;
    private String recipientName;
    private String recipientPhone;
    private String postalCode;
    private String address;
    private String addressDetail;
    private String courierCompany;
    private String trackingNumber;
    private DeliveryStatus status;
    private boolean paymentConfirmed;
    private boolean paymentVerified;

    public static DeliveryInfoResponse from(DeliveryInfo info) {
        return DeliveryInfoResponse.builder()
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
}
