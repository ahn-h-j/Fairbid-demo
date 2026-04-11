package com.cos.fairbid.ai.adapter.out.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 네이버 검색 API 설정.
 * application.yml 의 {@code naver.search.*} 항목을 바인딩한다.
 *
 * v2 Phase 1 — 외부 검색 API 분리. NAVER_CLIENT_ID/SECRET 는 OAuth2 로그인과 동일한
 * 환경변수를 재사용한다 (네이버 Developers Console 에서 같은 앱에 검색 API 권한이 활성화된 가정).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "naver.search")
public class NaverSearchProperties {

    /** 네이버 Open API Client ID — 환경변수 NAVER_CLIENT_ID */
    private String clientId;

    /** 네이버 Open API Client Secret — 환경변수 NAVER_CLIENT_SECRET */
    private String clientSecret;

    /** 네이버 Open API base URL */
    private String baseUrl = "https://openapi.naver.com";

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 3_000;

    /** 응답 대기 타임아웃 (ms) */
    private int readTimeoutMs = 5_000;
}
