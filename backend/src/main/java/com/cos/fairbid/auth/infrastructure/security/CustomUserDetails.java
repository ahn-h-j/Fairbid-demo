package com.cos.fairbid.auth.infrastructure.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

/**
 * Spring Security UserDetails 구현체
 * JWT에서 추출한 사용자 정보를 SecurityContext에 저장하기 위한 객체이다.
 *
 * - userId: 사용자 고유 ID
 * - nickname: 사용자 닉네임 (온보딩 전이면 null)
 * - onboarded: 온보딩 완료 여부
 * - role: 사용자 역할 (USER, ADMIN)
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String nickname;
    private final boolean onboarded;
    private final String role;

    public CustomUserDetails(Long userId, String nickname, boolean onboarded, String role) {
        this.userId = userId;
        this.nickname = nickname;
        this.onboarded = onboarded;
        this.role = role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ADMIN이면 ROLE_ADMIN, 그 외에는 ROLE_USER
        String authority = "ADMIN".equals(role) ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return null; // OAuth 전용이므로 비밀번호 없음
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
