package com.cos.fairbid.auth.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * Refresh Token 재사용 감지 예외
 * Token Rotation 정책에서 이미 사용된 Refresh Token이 다시 사용될 때 발생한다.
 * 이 경우 해당 사용자의 모든 세션을 무효화하여 보안을 강화한다.
 * HTTP 401 Unauthorized에 매핑된다.
 */
public class RefreshTokenReusedException extends DomainException {

    private final Long userId;

    private RefreshTokenReusedException(String errorCode, String message, Long userId) {
        super(errorCode, message);
        this.userId = userId;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    /**
     * Refresh Token 재사용 감지 시 사용한다.
     *
     * @param userId 탈취 의심 대상 사용자 ID
     */
    public static RefreshTokenReusedException detected(Long userId) {
        return new RefreshTokenReusedException(
                "REFRESH_TOKEN_REUSED",
                "Refresh Token 재사용이 감지되었습니다. 보안을 위해 모든 세션이 종료됩니다.",
                userId);
    }

    public Long getUserId() {
        return userId;
    }
}
