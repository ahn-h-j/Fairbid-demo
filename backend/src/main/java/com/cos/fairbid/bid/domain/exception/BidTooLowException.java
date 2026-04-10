package com.cos.fairbid.bid.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 입찰 금액이 최소 입찰 가능 금액보다 낮을 때 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
public class BidTooLowException extends DomainException {

    private BidTooLowException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 입찰가가 최소 입찰 가능 금액보다 낮을 때
     *
     * @param bidAmount    입찰 시도 금액
     * @param minBidAmount 최소 입찰 가능 금액 (현재가 + 입찰단위)
     * @return BidTooLowException 인스턴스
     */
    public static BidTooLowException belowMinimum(Long bidAmount, Long minBidAmount) {
        String message = String.format(
                "입찰 금액(%,d원)이 최소 입찰 가능 금액(%,d원)보다 낮습니다.",
                bidAmount, minBidAmount
        );
        return new BidTooLowException("BID_TOO_LOW", message);
    }
}
