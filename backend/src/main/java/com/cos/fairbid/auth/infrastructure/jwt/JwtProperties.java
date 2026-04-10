package com.cos.fairbid.auth.infrastructure.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * JWT 관련 설정 프로퍼티
 * application.yml의 jwt.* 프로퍼티를 바인딩한다.
 * @Validated로 애플리케이션 시작 시 설정값을 검증하여 fail-fast 한다.
 *
 * - secret-key: HMAC-SHA256 서명에 사용할 비밀 키 (Base64 인코딩)
 * - access-expiration: Access Token 만료 시간 (밀리초, 기본 30분)
 * - refresh-expiration: Refresh Token 만료 시간 (밀리초, 기본 2주)
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 서명에 사용할 비밀 키 (Base64 인코딩된 문자열)
     * 최소 256비트(32바이트) 이상이어야 한다.
     */
    @NotBlank(message = "JWT secret-key는 필수입니다.")
    private String secretKey;

    /**
     * Access Token 만료 시간 (밀리초)
     * 기본값: 1,800,000ms (30분)
     */
    @Positive(message = "access-expiration은 양수여야 합니다.")
    private long accessExpiration = 1_800_000;

    /**
     * Refresh Token 만료 시간 (밀리초)
     * 기본값: 1,209,600,000ms (2주)
     */
    @Positive(message = "refresh-expiration은 양수여야 합니다.")
    private long refreshExpiration = 1_209_600_000;
}
