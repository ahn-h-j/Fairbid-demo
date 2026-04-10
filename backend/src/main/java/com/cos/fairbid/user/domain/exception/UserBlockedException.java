package com.cos.fairbid.user.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 차단된 사용자 예외
 * 경고 3회 이상이거나 비활성화된 사용자가 로그인/액션 시도 시 발생한다.
 */
public class UserBlockedException extends DomainException {

    private UserBlockedException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    /**
     * 경고 횟수 초과로 차단된 경우
     */
    public static UserBlockedException byWarningCount() {
        return new UserBlockedException("USER_BLOCKED", "경고 누적으로 차단된 계정입니다.");
    }

    /**
     * 계정 비활성화(탈퇴)로 차단된 경우
     */
    public static UserBlockedException byDeactivation() {
        return new UserBlockedException("USER_DEACTIVATED", "탈퇴한 계정입니다.");
    }
}
