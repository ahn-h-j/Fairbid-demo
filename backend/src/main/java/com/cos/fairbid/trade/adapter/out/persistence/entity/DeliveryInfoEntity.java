package com.cos.fairbid.trade.adapter.out.persistence.entity;

import com.cos.fairbid.trade.domain.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 택배 배송 정보 JPA 엔티티
 * DB 테이블 매핑 전용 (비즈니스 로직 금지)
 */
@Entity
@Table(name = "delivery_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(length = 500)
    private String address;

    @Column(name = "address_detail", length = 200)
    private String addressDetail;

    @Column(name = "courier_company", length = 50)
    private String courierCompany;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "payment_confirmed", nullable = false)
    private boolean paymentConfirmed;

    @Column(name = "payment_verified", nullable = false)
    private boolean paymentVerified;

    @Builder
    private DeliveryInfoEntity(
            Long id,
            Long tradeId,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail,
            String courierCompany,
            String trackingNumber,
            DeliveryStatus status,
            boolean paymentConfirmed,
            boolean paymentVerified
    ) {
        this.id = id;
        this.tradeId = tradeId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.postalCode = postalCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.courierCompany = courierCompany;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.paymentConfirmed = paymentConfirmed;
        this.paymentVerified = paymentVerified;
    }
}
