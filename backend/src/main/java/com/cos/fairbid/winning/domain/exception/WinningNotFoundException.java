package com.cos.fairbid.winning.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 낙찰 정보를 찾을 수 없을 때 발생하는 예외
 * HTTP 404 Not Found에 매핑
 */
public class WinningNotFoundException extends DomainException {

    private WinningNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    public static WinningNotFoundException withId(Long id) {
        return new WinningNotFoundException(
                "WINNING_NOT_FOUND",
                "낙찰 정보를 찾을 수 없습니다. ID: " + id
        );
    }

    public static WinningNotFoundException withAuctionId(Long auctionId) {
        return new WinningNotFoundException(
                "WINNING_NOT_FOUND",
                "해당 경매의 낙찰 정보를 찾을 수 없습니다. 경매 ID: " + auctionId
        );
    }
}
