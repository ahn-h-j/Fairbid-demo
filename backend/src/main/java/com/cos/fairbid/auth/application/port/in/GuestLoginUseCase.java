package com.cos.fairbid.auth.application.port.in;

/**
 * 게스트 체험(데모) 로그인 유스케이스
 *
 * 소셜 인증(OAuth) 없이 임시 데모 계정을 즉석에서 발급한다.
 * 이력서/포트폴리오 데모에서 면접관이 회원가입·로그인 없이 바로 전체 기능을 체험할 수 있도록 한다.
 *
 * 발급되는 게스트 계정은 온보딩(닉네임/전화번호)까지 자동 완료된 상태라 추가 입력 없이 곧바로 사용 가능하다.
 */
public interface GuestLoginUseCase {

    /**
     * 새 게스트 계정을 생성하고 로그인 결과(토큰 + 사용자)를 반환한다.
     * 호출할 때마다 매번 독립된 임시 계정이 만들어진다. (세션 간 데이터 격리)
     *
     * @return 로그인 결과 (OAuth 로그인과 동일한 LoginResult 구조 재사용)
     */
    OAuthLoginUseCase.LoginResult guestLogin();
}
