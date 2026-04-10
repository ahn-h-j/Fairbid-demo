package com.cos.fairbid.admin.adapter.in.dto;

import java.time.LocalDateTime;

import lombok.Builder;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import com.cos.fairbid.user.domain.UserRole;

/**
 * 관리자용 유저 응답 DTO
 */
@Builder
public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        String phoneNumber,
        OAuthProvider provider,
        UserRole role,
        int warningCount,
        boolean isActive,
        boolean isBlocked,
        boolean isOnboarded,
        LocalDateTime createdAt
) {
    /**
     * Domain → Response DTO 변환
     *
     * @param user 유저 도메인 객체
     * @return 관리자용 유저 응답 DTO
     */
    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .phoneNumber(maskPhoneNumber(user.getPhoneNumber()))
                .provider(user.getProvider())
                .role(user.getRole())
                .warningCount(user.getWarningCount())
                .isActive(user.isActive())
                .isBlocked(user.isBlocked())
                .isOnboarded(user.isOnboarded())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 전화번호 마스킹 (010-****-5678)
     * 정규식을 사용하여 다양한 형식의 전화번호를 처리한다.
     * - 010-1234-5678 → 010-****-5678
     * - 01012345678 → 010-****-5678
     */
    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }
        // 정규식으로 전화번호 형식 매칭 및 마스킹
        return phoneNumber.replaceAll("(\\d{3})[-]?(\\d{4})[-]?(\\d{4})", "$1-****-$3");
    }
}
