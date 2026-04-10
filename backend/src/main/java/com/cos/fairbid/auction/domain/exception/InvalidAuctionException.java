package com.cos.fairbid.auction.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 경매 도메인 검증 예외
 * 경매 생성/수정 시 비즈니스 규칙 위반 시 발생
 * HTTP 400 Bad Request에 매핑
 */
public class InvalidAuctionException extends DomainException {

    private InvalidAuctionException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 즉시구매가가 시작가보다 낮거나 같을 때
     */
    public static InvalidAuctionException instantBuyPriceTooLow(Long startPrice, Long instantBuyPrice) {
        String message = String.format(
                "즉시구매가(%d)는 시작가(%d)보다 높아야 합니다.",
                instantBuyPrice, startPrice
        );
        return new InvalidAuctionException("INSTANT_BUY_PRICE_TOO_LOW", message);
    }

    /**
     * 거래 방식이 선택되지 않았을 때
     */
    public static InvalidAuctionException noTradeMethodSelected() {
        return new InvalidAuctionException(
                "NO_TRADE_METHOD_SELECTED",
                "거래 방식을 최소 1개 이상 선택해야 합니다. (직거래 또는 택배)"
        );
    }

    /**
     * 직거래 선택 시 위치가 입력되지 않았을 때
     */
    public static InvalidAuctionException directTradeLocationRequired() {
        return new InvalidAuctionException(
                "DIRECT_TRADE_LOCATION_REQUIRED",
                "직거래를 선택한 경우 거래 희망 위치를 입력해야 합니다."
        );
    }
}
