package com.cos.fairbid.auth.infrastructure.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 설정
 *
 * - JWT 기반 Stateless 인증
 * - CSRF 비활성화 (SameSite 쿠키 + JWT로 방어)
 * - CORS 설정 (프론트엔드 도메인 허용)
 * - 접근 제어:
 *   - GET /api/v1/auctions/** → 비로그인 허용 (경매 조회)
 *   - /api/v1/auth/** → 비로그인 허용 (인증 관련)
 *   - /ws/** → 비로그인 허용 (WebSocket)
 *   - /health, /actuator/** → 비로그인 허용 (모니터링)
 *   - /api/v1/test/** → ADMIN만 허용 (개발용 테스트)
 *   - 그 외 → 인증 필요
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // @PreAuthorize, @PostAuthorize 활성화
@Profile("!test & !load-test")
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화: SameSite=Strict 쿠키 + JWT 조합으로 CSRF 공격 방어
                .csrf(AbstractHttpConfigurer::disable)

                // CORS 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 미사용: JWT 기반 Stateless 인증
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 접근 제어 규칙
                .authorizeHttpRequests(auth -> auth
                        // 인증 관련 엔드포인트
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // 닉네임 중복 확인 (비인증 허용)
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/check-nickname").permitAll()

                        // 경매 조회 (GET만 비로그인 허용)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auctions/**").permitAll()

                        // WebSocket 엔드포인트
                        .requestMatchers("/ws/**").permitAll()

                        // 헬스체크, 모니터링
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        // 개발용 테스트 엔드포인트 (ADMIN 역할 필요)
                        // — 시뮬레이션 Mock 로그인(/api/v1/test/auth/**)은 SimulationSecurityConfig 에서
                        //   @Profile("simulation") 격리된 별도 SecurityFilterChain 으로 처리한다.
                        .requestMatchers("/api/v1/test/**").hasRole("ADMIN")

                        // 관리자 전용 엔드포인트 (ADMIN 역할 필요)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 설정
     * 프론트엔드 도메인에서의 요청을 허용한다.
     * credentials: true로 설정하여 쿠키(Refresh Token) 전송을 허용한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
