package com.cos.fairbid.user.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 이미 온보딩을 완료한 사용자가 다시 온보딩을 시도할 때 발생하는 예외
 * HTTP 400 Bad Request
 */
public class AlreadyOnboardedException extends DomainException {

    private AlreadyOnboardedException() {
        super("ALREADY_ONBOARDED", "이미 온보딩이 완료된 사용자입니다.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static AlreadyOnboardedException create() {
        return new AlreadyOnboardedException();
    }
}
