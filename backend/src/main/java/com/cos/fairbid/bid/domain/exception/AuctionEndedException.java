package com.cos.fairbid.bid.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 경매가 이미 종료되었을 때 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
public class AuctionEndedException extends DomainException {

    private AuctionEndedException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 종료된 경매에 입찰 시도 시 발생
     *
     * @param auctionId 경매 ID
     * @return AuctionEndedException 인스턴스
     */
    public static AuctionEndedException forBid(Long auctionId) {
        String message = String.format("이미 종료된 경매입니다. (경매 ID: %d)", auctionId);
        return new AuctionEndedException("AUCTION_ENDED", message);
    }
}
