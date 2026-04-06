package com.cos.fairbid.user.adapter.out.persistence.entity;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * User JPA 엔티티
 * 비즈니스 로직 없이 DB 매핑만 담당한다.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname"),
        @UniqueConstraint(name = "uk_users_phone_number", columnNames = "phone_number"),
        @UniqueConstraint(name = "uk_users_provider_id", columnNames = {"provider", "provider_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String nickname;

    @Column(name = "phone_number", length = 13)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OAuthProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 배송지 정보
    @Column(name = "shipping_recipient_name", length = 100)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    @Column(name = "shipping_postal_code", length = 10)
    private String shippingPostalCode;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "shipping_address_detail", length = 200)
    private String shippingAddressDetail;

    // 계좌 정보 (판매 대금 수령용)
    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Builder
    public UserEntity(Long id, String email, String nickname, String phoneNumber,
                      OAuthProvider provider, String providerId, UserRole role,
                      Integer warningCount, Boolean isActive,
                      LocalDateTime createdAt, LocalDateTime updatedAt,
                      String shippingRecipientName, String shippingPhone,
                      String shippingPostalCode, String shippingAddress, String shippingAddressDetail,
                      String bankName, String accountNumber, String accountHolder) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
        this.warningCount = warningCount;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.shippingRecipientName = shippingRecipientName;
        this.shippingPhone = shippingPhone;
        this.shippingPostalCode = shippingPostalCode;
        this.shippingAddress = shippingAddress;
        this.shippingAddressDetail = shippingAddressDetail;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}
