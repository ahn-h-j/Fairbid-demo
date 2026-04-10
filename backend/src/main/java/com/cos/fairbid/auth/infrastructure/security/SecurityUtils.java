package com.cos.fairbid.auth.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.cos.fairbid.common.exception.UnauthorizedException;

/**
 * Security 유틸리티 클래스
 * SecurityContext에서 현재 인증된 사용자 정보를 추출하는 정적 메서드를 제공한다.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // 인스턴스화 방지
    }

    /**
     * 현재 인증된 사용자의 ID를 반환한다.
     * 인증되지 않은 상태에서 호출하면 예외가 발생한다.
     *
     * @return 현재 사용자 ID
     * @throws UnauthorizedException 인증되지 않은 상태에서 호출 시
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw UnauthorizedException.notAuthenticated();
        }
        return userDetails.getUserId();
    }

    /**
     * 현재 인증된 사용자의 ID를 반환한다.
     * 인증되지 않은 상태에서 호출하면 null을 반환한다.
     *
     * @return 현재 사용자 ID (인증되지 않은 경우 null)
     */
    public static Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails.getUserId();
    }

    /**
     * 현재 인증된 사용자의 닉네임을 반환한다.
     *
     * @return 현재 사용자 닉네임 (온보딩 전이면 null)
     */
    public static String getCurrentNickname() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails.getNickname();
    }

    /**
     * 현재 사용자가 온보딩을 완료했는지 확인한다.
     *
     * @return 온보딩 완료 여부
     */
    public static boolean isOnboarded() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return false;
        }
        return userDetails.isOnboarded();
    }

}
