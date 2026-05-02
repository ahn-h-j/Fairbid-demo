package com.cos.fairbid.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase;
import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase.LoginResult;
import com.cos.fairbid.auth.application.port.in.SimulationLoginUseCase;
import com.cos.fairbid.auth.application.port.out.OAuthUserInfo;
import com.cos.fairbid.auth.application.port.out.RefreshTokenPort;
import com.cos.fairbid.auth.application.port.out.TokenProviderPort;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import com.cos.fairbid.user.domain.UserRole;

/**
 * 시뮬레이션 전용 Mock 로그인 서비스.
 *
 * 컨트롤러에서 분산되어 있던 OAuth 로그인 → 온보딩 → ADMIN 승격 → 토큰 재발급 흐름을
 * 한 트랜잭션 안에서 처리한다. 헥사고날 의존성 방향(Controller → UseCase → Domain) 유지를 위해
 * 컨트롤러는 이 UseCase만 의존하고, Port Out(SaveUserPort, RefreshTokenPort, TokenProviderPort)은
 * 본 서비스에서 캡슐화한다.
 *
 * 격리: @Profile("simulation") — 운영/개발 환경에서는 빈 등록되지 않는다.
 */
@Slf4j
@Service
@Profile("simulation")
@RequiredArgsConstructor
public class SimulationLoginService implements SimulationLoginUseCase {

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final SaveUserPort saveUserPort;
    private final TokenProviderPort tokenProviderPort;
    private final RefreshTokenPort refreshTokenPort;

    @Override
    @Transactional
    public SimulationLoginResult mockLogin(SimulationLoginCommand command) {
        OAuthUserInfo userInfo = buildMockUserInfo(command.email());

        // 1. 진짜 인증 흐름 통과 (User 생성/조회 + JWT 발급 + Refresh Redis 저장)
        LoginResult result = oAuthLoginUseCase.loginWithUserInfo(userInfo);
        User user = result.user();

        // 2. 온보딩 자동 완료 (신규 사용자만)
        //    onboarded=false claim 으로 발급된 토큰은 @RequireOnboarding 가드를 통과 못 하므로
        //    완료 후 토큰을 재발급한다.
        if (!user.isOnboarded()) {
            user.completeOnboarding(command.nickname(), command.phoneNumber());
            saveUserPort.save(user);
            result = oAuthLoginUseCase.loginWithUserInfo(userInfo);
            user = result.user();
            log.debug("[SIM] 온보딩 자동 완료 + 토큰 재발급: userId={}", user.getId());
        }

        // 3. admin=true 요청이면 ADMIN 승격 후 토큰 직접 재발급한다.
        //    loginWithUserInfo 재호출을 쓰지 않는 이유:
        //    AuthService.loginWithUserInfo() 는 determineRole(email) 을 재계산해서
        //    ADMIN_EMAILS에 없는 이메일은 USER 로 강제 동기화한다. 즉 방금 박은 ADMIN이
        //    다음 줄에서 USER 로 되돌아간다. 따라서 DB 상태를 보존하기 위해
        //    토큰 발급/Refresh 저장을 포트로 직접 호출한다.
        if (command.admin() && !user.isAdmin()) {
            user.updateRole(UserRole.ADMIN);
            user = saveUserPort.save(user);
            String adminAccessToken = tokenProviderPort.generateAccessToken(user);
            String adminRefreshToken = tokenProviderPort.generateRefreshToken(user);
            refreshTokenPort.save(
                    user.getId(),
                    adminRefreshToken,
                    tokenProviderPort.getRefreshExpirationSeconds()
            );
            result = new LoginResult(user, adminAccessToken, adminRefreshToken, result.isNewUser());
            log.debug("[SIM] ADMIN 승격 + 토큰 재발급: userId={}", user.getId());
        }

        log.debug("[SIM] Mock 로그인 완료: userId={}, isNewUser={}", user.getId(), result.isNewUser());

        return new SimulationLoginResult(user.getId(), result.accessToken(), result.refreshToken());
    }

    /**
     * email 기반 deterministic providerId로 가짜 OAuthUserInfo 를 만든다.
     * 같은 email 재로그인 시 같은 User 가 재사용되도록 보장한다.
     */
    private OAuthUserInfo buildMockUserInfo(String email) {
        String providerId = "test-" + sha256Short(email);
        return new OAuthUserInfo(email, providerId, OAuthProvider.KAKAO);
    }

    /**
     * SHA-256 해시 앞 16자리를 반환한다 (8바이트 hex).
     * email → providerId 매핑에 사용되며, 같은 email은 항상 같은 providerId가 된다.
     */
    private String sha256Short(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK에 항상 존재 — 발생 불가능
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
