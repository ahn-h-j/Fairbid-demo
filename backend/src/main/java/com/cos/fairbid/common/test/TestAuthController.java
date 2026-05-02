package com.cos.fairbid.common.test;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.application.port.in.SimulationLoginUseCase;
import com.cos.fairbid.auth.application.port.in.SimulationLoginUseCase.SimulationLoginCommand;
import com.cos.fairbid.auth.application.port.in.SimulationLoginUseCase.SimulationLoginResult;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;

/**
 * 시뮬레이션 전용 Mock OAuth 로그인 컨트롤러.
 *
 * 시뮬 페르소나가 외부 OAuth Provider 호출 없이 진짜 인증 흐름(User 생성 + JWT + Refresh Redis 저장)을
 * 통과하기 위해 사용한다. 실제 인증/온보딩/ADMIN 승격 흐름은 {@link SimulationLoginUseCase} 가 한 트랜잭션으로 처리한다.
 *
 * 활성화 조건 (다층 격리):
 * - simulation 프로파일 (@Profile)
 * - api / all 서버 역할 (@EnabledOnRole)
 * - SecurityFilterChain 도 simulation 프로파일에서만 등록된다 ({@code SimulationSecurityConfig})
 *
 * 운영/개발 환경에서는 절대 활성화되어서는 안 된다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/auth")
@RequiredArgsConstructor
@Profile("simulation")
@EnabledOnRole({"api", "all"})
public class TestAuthController {

    private final SimulationLoginUseCase simulationLoginUseCase;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MockLoginResponse>> mockLogin(
            @Valid @RequestBody MockLoginRequest request
    ) {
        SimulationLoginResult result = simulationLoginUseCase.mockLogin(
                new SimulationLoginCommand(
                        request.email(),
                        request.nickname(),
                        request.phoneNumber(),
                        Boolean.TRUE.equals(request.admin())
                )
        );

        log.debug("[SIM] Mock 로그인 응답: userId={}", result.userId());

        return ResponseEntity.ok(ApiResponse.success(
                new MockLoginResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        result.userId()
                )
        ));
    }

    /**
     * Mock 로그인 요청.
     *
     * @param email       사용자 이메일 (providerId 결정 키)
     * @param nickname    온보딩 닉네임 (신규 사용자 시 자동 설정)
     * @param phoneNumber 온보딩 전화번호 (10~11자리 숫자)
     * @param admin       true 면 ADMIN 으로 승격 후 토큰 재발급 (선택)
     */
    public record MockLoginRequest(
            @NotBlank @Email
            String email,

            @NotBlank
            String nickname,

            @NotBlank
            @Pattern(regexp = "\\d{10,11}", message = "전화번호는 하이픈 없이 10~11자리 숫자여야 합니다.")
            String phoneNumber,

            Boolean admin
    ) { }

    /**
     * Mock 로그인 응답.
     * 시뮬레이션은 헤더 기반이라 refreshToken을 본문에 노출한다 (운영 흐름은 SameSite 쿠키).
     *
     * @param accessToken  발급된 Access Token (Bearer 헤더로 사용)
     * @param refreshToken 발급된 Refresh Token
     * @param userId       발급된/조회된 사용자 ID
     */
    public record MockLoginResponse(
            String accessToken,
            String refreshToken,
            Long userId
    ) { }
}
