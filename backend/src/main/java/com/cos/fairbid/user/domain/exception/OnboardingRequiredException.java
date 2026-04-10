package com.cos.fairbid.user.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 온보딩 미완료 상태에서 온보딩 필수 기능에 접근 시 발생하는 예외
 * HTTP 403 Forbidden
 */
public class OnboardingRequiredException extends DomainException {

    private OnboardingRequiredException() {
        super("ONBOARDING_REQUIRED", "온보딩을 완료해야 이용할 수 있습니다.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    public static OnboardingRequiredException create() {
        return new OnboardingRequiredException();
    }
}
