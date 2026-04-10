package com.cos.fairbid.bid.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 본인이 등록한 경매에 입찰 시도 시 발생하는 예외
 * HTTP 403 Forbidden에 매핑
 */
public class SelfBidNotAllowedException extends DomainException {

    private SelfBidNotAllowedException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    /**
     * 판매자가 본인 경매에 입찰 시도 시
     *
     * @return SelfBidNotAllowedException 인스턴스
     */
    public static SelfBidNotAllowedException create() {
        return new SelfBidNotAllowedException("SELF_BID_NOT_ALLOWED", "본인이 등록한 경매에는 입찰할 수 없습니다.");
    }
}
