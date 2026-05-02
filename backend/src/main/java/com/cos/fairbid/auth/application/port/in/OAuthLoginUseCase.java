package com.cos.fairbid.auth.application.port.in;

import com.cos.fairbid.auth.application.port.out.OAuthUserInfo;
import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;

/**
 * OAuth 로그인 유스케이스
 * Authorization Code를 사용하여 로그인 또는 회원가입을 처리한다.
 */
public interface OAuthLoginUseCase {

    /**
     * OAuth 로그인을 수행한다.
     * 기존 사용자면 로그인, 신규면 회원가입 후 로그인한다.
     *
     * @param provider OAuth Provider 종류
     * @param code     Authorization Code
     * @return 로그인 결과 (토큰 + 사용자 정보)
     */
    LoginResult login(OAuthProvider provider, String code);

    /**
     * OAuth 사용자 정보로 로그인을 수행한다.
     * Authorization Code → UserInfo 교환 단계를 건너뛰고, 이미 확보된 OAuthUserInfo로 로그인 흐름을 진행한다.
     * 시뮬레이션/테스트 환경에서 외부 OAuth Provider 호출 없이 진짜 인증 흐름을 통과시킬 때 사용한다.
     *
     * @param userInfo OAuth Provider로부터 받은 사용자 정보 (또는 mock 데이터)
     * @return 로그인 결과 (토큰 + 사용자 정보)
     */
    LoginResult loginWithUserInfo(OAuthUserInfo userInfo);

    /**
     * 로그인 결과 record
     *
     * @param user         로그인/가입된 사용자
     * @param accessToken  Access Token
     * @param refreshToken Refresh Token
     * @param isNewUser    신규 가입 여부 (온보딩 필요 판단용)
     */
    record LoginResult(
            User user,
            String accessToken,
            String refreshToken,
            boolean isNewUser
    ) {
    }
}
