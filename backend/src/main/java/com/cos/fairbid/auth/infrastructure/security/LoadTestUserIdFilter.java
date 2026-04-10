package com.cos.fairbid.auth.infrastructure.security;

import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 부하 테스트용 인증 필터 (load-test 프로필 전용)
 *
 * X-User-Id 헤더의 값을 userId로 사용하여 SecurityContext에 인증 정보를 설정한다.
 * JWT 없이 간편하게 인증을 우회하여 k6 부하 테스트를 가능하게 한다.
 *
 * 프로덕션 환경에서는 절대 활성화하면 안 된다. (load-test 프로필에서만 Bean 등록)
 */
@Slf4j
@Component
@Profile("load-test")
public class LoadTestUserIdFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdStr = request.getHeader(USER_ID_HEADER);

        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId = Long.parseLong(userIdStr);

                // onboarded=true, role=USER 로 설정하여 모든 비즈니스 로직 통과
                CustomUserDetails userDetails = new CustomUserDetails(
                        userId, "load-test-user-" + userId, true, "USER");

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (NumberFormatException e) {
                log.warn("X-User-Id 헤더 파싱 실패: {}", userIdStr);
            }
        }

        filterChain.doFilter(request, response);
    }
}
