package com.cos.fairbid.auth.infrastructure.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.domain.exception.TokenExpiredException;
import com.cos.fairbid.auth.domain.exception.TokenInvalidException;
import com.cos.fairbid.auth.infrastructure.jwt.JwtTokenProvider;

/**
 * JWT 인증 필터
 * 모든 요청에 대해 Authorization 헤더에서 Bearer 토큰을 추출하고,
 * 유효한 토큰이면 SecurityContext에 Authentication을 설정한다.
 *
 * 토큰이 없거나 유효하지 않은 경우 필터를 통과시켜
 * Security의 접근 제어(permitAll/authenticated)가 최종 판단하도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. Authorization 헤더에서 토큰 추출
        String token = extractToken(request);

        // 2. 토큰이 있으면 검증 후 SecurityContext에 설정
        if (token != null) {
            try {
                Claims claims = jwtTokenProvider.validateToken(token);
                Long userId = Long.parseLong(claims.getSubject());
                String nickname = claims.get("nickname", String.class);
                Boolean onboarded = claims.get("onboarded", Boolean.class);
                String role = claims.get("role", String.class);

                CustomUserDetails userDetails = new CustomUserDetails(
                        userId, nickname, onboarded != null && onboarded, role);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT 인증 성공: userId={}, role={}", userId, role);
            } catch (TokenExpiredException | TokenInvalidException e) {
                // JWT 관련 예외: 토큰 만료/무효 → SecurityContext 비워둠
                log.debug("JWT 인증 실패: {}", e.getMessage());
            } catch (Exception e) {
                // 예상치 못한 예외: 로깅 후 통과 (접근 제어에서 처리)
                log.warn("JWT 처리 중 예상치 못한 오류: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 Bearer 토큰을 추출한다.
     *
     * @param request HTTP 요청
     * @return 토큰 문자열 (없으면 null)
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
