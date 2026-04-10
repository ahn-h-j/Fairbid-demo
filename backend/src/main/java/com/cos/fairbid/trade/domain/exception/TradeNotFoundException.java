package com.cos.fairbid.trade.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 거래를 찾을 수 없을 때 발생하는 예외
 * HTTP 404 Not Found에 매핑
 */
public class TradeNotFoundException extends DomainException {

    private TradeNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    public static TradeNotFoundException withId(Long tradeId) {
        return new TradeNotFoundException(
                "TRADE_NOT_FOUND",
                String.format("거래를 찾을 수 없습니다. ID: %d", tradeId)
        );
    }

    public static TradeNotFoundException withAuctionId(Long auctionId) {
        return new TradeNotFoundException(
                "TRADE_NOT_FOUND",
                String.format("해당 경매의 거래를 찾을 수 없습니다. 경매 ID: %d", auctionId)
        );
    }
}
