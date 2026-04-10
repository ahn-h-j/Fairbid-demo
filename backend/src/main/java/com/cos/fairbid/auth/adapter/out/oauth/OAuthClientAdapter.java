package com.cos.fairbid.auth.adapter.out.oauth;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.auth.application.port.out.OAuthClientPort;
import com.cos.fairbid.auth.application.port.out.OAuthUserInfo;
import com.cos.fairbid.user.domain.OAuthProvider;

/**
 * OAuth 클라이언트 라우팅 어댑터
 * OAuthClientPort를 구현하며, Provider 종류에 따라 적절한 OAuth 클라이언트를 위임한다.
 */
@Component
@RequiredArgsConstructor
public class OAuthClientAdapter implements OAuthClientPort {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;

    @Override
    public OAuthUserInfo getUserInfo(OAuthProvider provider, String code) {
        return switch (provider) {
            case KAKAO -> kakaoOAuthClient.getUserInfo(code);
            case NAVER -> naverOAuthClient.getUserInfo(code);
            case GOOGLE -> googleOAuthClient.getUserInfo(code);
        };
    }
}
