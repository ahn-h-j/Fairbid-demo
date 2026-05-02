package com.cos.fairbid.auth.application.port.in;

/**
 * 시뮬레이션 전용 Mock 로그인 유스케이스.
 *
 * AI 에이전트 경매 시뮬레이션에서 외부 OAuth Provider 호출 없이
 * 진짜 인증 흐름(User 생성/조회 + 온보딩 자동 완료 + ADMIN 승격 + JWT 발급 + Refresh Redis 저장)을
 * 한 트랜잭션으로 통과시킨다.
 *
 * 운영/개발 환경에서는 빈 등록되지 않아야 한다 (구현체에 @Profile("simulation") 격리).
 */
public interface SimulationLoginUseCase {

    /**
     * 시뮬레이션 Mock 로그인을 수행한다.
     *
     * 흐름:
     * 1. email 기반 deterministic providerId로 OAuth 로그인 흐름 통과
     * 2. 신규 사용자면 nickname/phoneNumber 자동 설정 (온보딩 완료) + 토큰 재발급
     * 3. admin=true 요청이면 ADMIN 역할로 승격 + 토큰 직접 재발급
     *
     * @param command Mock 로그인 입력값
     * @return 발급된 토큰 + 사용자 ID
     */
    SimulationLoginResult mockLogin(SimulationLoginCommand command);

    record SimulationLoginCommand(
            String email,
            String nickname,
            String phoneNumber,
            boolean admin
    ) {
    }

    record SimulationLoginResult(
            Long userId,
            String accessToken,
            String refreshToken
    ) {
    }
}
