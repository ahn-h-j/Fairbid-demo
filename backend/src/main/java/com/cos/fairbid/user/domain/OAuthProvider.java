package com.cos.fairbid.user.domain;

/**
 * OAuth2 인증 제공자 열거형
 * 지원하는 소셜 로그인 Provider를 정의한다.
 */
public enum OAuthProvider {
    KAKAO,
    NAVER,
    GOOGLE,
    // 게스트 체험(데모) 로그인용. 실제 소셜 인증 없이 임시 데모 계정을 발급할 때 사용한다.
    DEMO
}
