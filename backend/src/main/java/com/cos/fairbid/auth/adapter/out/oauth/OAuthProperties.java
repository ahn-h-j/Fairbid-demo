package com.cos.fairbid.auth.adapter.out.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * OAuth2 클라이언트 설정 프로퍼티
 * 각 Provider별 client-id, client-secret, redirect-uri를 바인딩한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oauth2")
public class OAuthProperties {

    private Provider kakao = new Provider();
    private Provider naver = new Provider();
    private Provider google = new Provider();

    @Getter
    @Setter
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}
