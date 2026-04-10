package com.cos.fairbid.user.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 사용자 조회 실패 예외
 */
public class UserNotFoundException extends DomainException {

    private UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    public static UserNotFoundException withId(Long userId) {
        return new UserNotFoundException("사용자를 찾을 수 없습니다. (ID: " + userId + ")");
    }
}
