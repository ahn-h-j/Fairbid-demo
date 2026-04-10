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
 * 구글 OAuth2 클라이언트
 *
 * 인증 흐름:
 * 1. Authorization Code → Access Token 교환 (POST https://oauth2.googleapis.com/token)
 * 2. Access Token → 사용자 정보 조회 (GET https://www.googleapis.com/oauth2/v2/userinfo)
 */
@Slf4j
@Component
public class GoogleOAuthClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    private final RestClient restClient;
    private final OAuthProperties oAuthProperties;

    public GoogleOAuthClient(OAuthProperties oAuthProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.oAuthProperties = oAuthProperties;
    }

    /**
     * Authorization Code로 구글 사용자 정보를 조회한다.
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
            throw new IllegalStateException("구글 사용자 정보 응답이 비어있습니다.");
        }

        // 3. 응답에서 이메일, providerId 추출
        String providerId = (String) response.get("id");
        String email = (String) response.get("email");

        if (providerId == null || providerId.isBlank()) {
            throw new IllegalStateException("구글 사용자 ID를 추출할 수 없습니다.");
        }

        if (email == null || email.isBlank()) {
            throw OAuthEmailRequiredException.from("GOOGLE");
        }

        log.debug("구글 로그인 성공: providerId={}", providerId);
        return new OAuthUserInfo(email, providerId, OAuthProvider.GOOGLE);
    }

    /**
     * Authorization Code를 Access Token으로 교환한다.
     */
    @SuppressWarnings("unchecked")
    private String getAccessToken(String code) {
        OAuthProperties.Provider google = oAuthProperties.getGoogle();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", google.getClientId());
        params.add("client_secret", google.getClientSecret());
        params.add("redirect_uri", google.getRedirectUri());
        params.add("code", code);

        Map<String, Object> response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            String error = response != null ? String.valueOf(response.get("error")) : "null response";
            throw new IllegalStateException("구글 Access Token 발급 실패: " + error);
        }

        return (String) response.get("access_token");
    }
}
