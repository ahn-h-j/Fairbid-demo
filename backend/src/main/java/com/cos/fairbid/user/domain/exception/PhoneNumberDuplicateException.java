package com.cos.fairbid.user.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 전화번호 중복 예외
 * 동일 전화번호로 이미 가입된 계정이 있을 때 발생한다.
 * 노쇼 패널티 회피를 위한 멀티 계정 방지 목적.
 */
public class PhoneNumberDuplicateException extends DomainException {

    private PhoneNumberDuplicateException() {
        super("PHONE_NUMBER_DUPLICATE", "이미 등록된 전화번호입니다.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static PhoneNumberDuplicateException create() {
        return new PhoneNumberDuplicateException();
    }
}
