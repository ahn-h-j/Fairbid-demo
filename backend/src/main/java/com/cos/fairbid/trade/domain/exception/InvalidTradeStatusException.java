package com.cos.fairbid.trade.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;
import com.cos.fairbid.trade.domain.TradeStatus;

/**
 * 거래 상태가 유효하지 않을 때 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
public class InvalidTradeStatusException extends DomainException {

    private InvalidTradeStatusException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static InvalidTradeStatusException forAction(String action, TradeStatus currentStatus) {
        return new InvalidTradeStatusException(
                "INVALID_TRADE_STATUS",
                String.format("%s을(를) 수행할 수 없는 상태입니다. 현재 상태: %s", action, currentStatus)
        );
    }

    public static InvalidTradeStatusException methodAlreadySelected() {
        return new InvalidTradeStatusException(
                "METHOD_ALREADY_SELECTED",
                "거래 방식이 이미 선택되었습니다."
        );
    }

    public static InvalidTradeStatusException responseExpired() {
        return new InvalidTradeStatusException(
                "RESPONSE_DEADLINE_EXPIRED",
                "응답 기한이 만료되었습니다."
        );
    }

    public static InvalidTradeStatusException cannotSelectMethod(TradeStatus currentStatus) {
        return new InvalidTradeStatusException(
                "CANNOT_SELECT_METHOD",
                String.format("거래 방식 선택이 불가능한 상태입니다. 현재 상태: %s", currentStatus)
        );
    }

    public static InvalidTradeStatusException notDirectTrade(Long tradeId) {
        return new InvalidTradeStatusException(
                "NOT_DIRECT_TRADE",
                String.format("직거래가 아닌 거래입니다. 거래 ID: %d", tradeId)
        );
    }

    public static InvalidTradeStatusException notDelivery(Long tradeId) {
        return new InvalidTradeStatusException(
                "NOT_DELIVERY_TRADE",
                String.format("택배 거래가 아닙니다. 거래 ID: %d", tradeId)
        );
    }
}
