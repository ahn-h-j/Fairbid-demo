package com.cos.fairbid.user.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import com.cos.fairbid.user.adapter.out.persistence.entity.UserEntity;
import com.cos.fairbid.user.domain.User;

/**
 * User 도메인 ↔ Entity 변환 매퍼
 * 도메인 레이어와 영속성 레이어 간의 변환을 담당한다.
 */
@Component
public class UserMapper {

    /**
     * 도메인 모델을 JPA 엔티티로 변환한다.
     *
     * @param user User 도메인 객체
     * @return UserEntity JPA 엔티티
     */
    public UserEntity toEntity(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .phoneNumber(user.getPhoneNumber())
                .provider(user.getProvider())
                .providerId(user.getProviderId())
                .role(user.getRole())
                .warningCount(user.getWarningCount())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .shippingRecipientName(user.getShippingRecipientName())
                .shippingPhone(user.getShippingPhone())
                .shippingPostalCode(user.getShippingPostalCode())
                .shippingAddress(user.getShippingAddress())
                .shippingAddressDetail(user.getShippingAddressDetail())
                .bankName(user.getBankName())
                .accountNumber(user.getAccountNumber())
                .accountHolder(user.getAccountHolder())
                .build();
    }

    /**
     * JPA 엔티티를 도메인 모델로 변환한다.
     * reconstitute() 팩토리를 사용하여 도메인 규칙 없이 복원한다.
     *
     * @param entity UserEntity JPA 엔티티
     * @return User 도메인 객체
     */
    public User toDomain(UserEntity entity) {
        return User.reconstitute()
                .id(entity.getId())
                .email(entity.getEmail())
                .nickname(entity.getNickname())
                .phoneNumber(entity.getPhoneNumber())
                .provider(entity.getProvider())
                .providerId(entity.getProviderId())
                .role(entity.getRole())
                .warningCount(entity.getWarningCount())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .shippingRecipientName(entity.getShippingRecipientName())
                .shippingPhone(entity.getShippingPhone())
                .shippingPostalCode(entity.getShippingPostalCode())
                .shippingAddress(entity.getShippingAddress())
                .shippingAddressDetail(entity.getShippingAddressDetail())
                .bankName(entity.getBankName())
                .accountNumber(entity.getAccountNumber())
                .accountHolder(entity.getAccountHolder())
                .build();
    }
}
