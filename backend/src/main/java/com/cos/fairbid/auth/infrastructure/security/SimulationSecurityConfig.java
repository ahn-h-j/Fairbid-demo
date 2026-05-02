package com.cos.fairbid.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 시뮬레이션 Mock 로그인 전용 SecurityFilterChain.
 *
 * {@code /api/v1/test/auth/**} 매처에만 적용되며, simulation 프로파일에서만 빈 등록된다.
 * 운영/개발 환경에서는 빈 등록 자체가 안 되므로 permitAll 매처도 함께 사라진다.
 *
 * 분리 이유:
 * 메인 {@link SecurityConfig} 에 permitAll 매처를 박아두면 다른 컨트롤러를 같은 경로 prefix
 * (예: {@code /api/v1/test/auth/foo}) 에 추가했을 때 운영에서도 인증 없이 접근되는 위험이 있다.
 * Profile-scoped FilterChain 으로 분리하면 simulation 외 환경에서는 매처 자체가 등록되지 않아
 * 운영 누출 표면이 사라진다.
 *
 * 우선순위: @Order(1) — 메인 SecurityConfig (default order, LOWEST_PRECEDENCE) 보다 먼저 평가된다.
 * securityMatcher 가 매칭되면 이 체인만 적용되고 메인 체인은 평가되지 않는다.
 */
@Configuration
@Profile("simulation")
public class SimulationSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain simulationAuthFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/test/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
