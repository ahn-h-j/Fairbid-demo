package com.cos.fairbid.auth.adapter.out.oauth;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.application.port.out.OAuthUserInfo;
import com.cos.fairbid.auth.domain.exception.OAuthEmailRequiredException;
import com.cos.fairbid.user.domain.OAuthProvider;

/**
 * 카카오 OAuth2 클라이언트
 *
 * 인증 흐름:
 * 1. Authorization Code → Access Token 교환 (POST https://kauth.kakao.com/oauth/token)
 * 2. Access Token → 사용자 정보 조회 (GET https://kapi.kakao.com/v2/user/me)
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final OAuthProperties oAuthProperties;

    public KakaoOAuthClient(OAuthProperties oAuthProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.oAuthProperties = oAuthProperties;
    }

    /**
     * Authorization Code로 카카오 사용자 정보를 조회한다.
     *
     * @param code Authorization Code
     * @return OAuth 사용자 정보
     */
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String code) {
        // 1. Code → Access Token 교환
        String accessToken = getAccessToken(code);

        // 2. Access Token → 사용자 정보 조회
        Map<String, Object> response = restClient.get()
                .uri(USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("카카오 사용자 정보 응답이 비어있습니다.");
        }

        // 3. 응답에서 이메일, providerId 추출
        String providerId = String.valueOf(response.get("id"));
        if (providerId == null || "null".equals(providerId)) {
            throw new IllegalStateException("카카오 사용자 ID를 추출할 수 없습니다.");
        }

        Object kakaoAccountObj = response.get("kakao_account");
        if (!(kakaoAccountObj instanceof Map)) {
            throw OAuthEmailRequiredException.from("KAKAO");
        }

        Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoAccountObj;
        String email = (String) kakaoAccount.get("email");
        if (email == null || email.isBlank()) {
            throw OAuthEmailRequiredException.from("KAKAO");
        }

        log.debug("카카오 로그인 성공: providerId={}", providerId);
        return new OAuthUserInfo(email, providerId, OAuthProvider.KAKAO);
    }

    /**
     * Authorization Code를 Access Token으로 교환한다.
     */
    @SuppressWarnings("unchecked")
    private String getAccessToken(String code) {
        OAuthProperties.Provider kakao = oAuthProperties.getKakao();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakao.getClientId());
        params.add("client_secret", kakao.getClientSecret());
        params.add("redirect_uri", kakao.getRedirectUri());
        params.add("code", code);

        Map<String, Object> response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            String error = response != null ? String.valueOf(response.get("error")) : "null response";
            throw new IllegalStateException("카카오 Access Token 발급 실패: " + error);
        }

        return (String) response.get("access_token");
    }
}
