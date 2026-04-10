package com.cos.fairbid.auction.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 경매를 찾을 수 없을 때 발생하는 예외
 * HTTP 404 Not Found에 매핑
 */
public class AuctionNotFoundException extends DomainException {

    private AuctionNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    /**
     * 경매 ID로 조회 시 경매가 존재하지 않을 때
     *
     * @param auctionId 조회한 경매 ID
     * @return AuctionNotFoundException 인스턴스
     */
    public static AuctionNotFoundException withId(Long auctionId) {
        String message = String.format("경매를 찾을 수 없습니다. (ID: %d)", auctionId);
        return new AuctionNotFoundException("AUCTION_NOT_FOUND", message);
    }
}
