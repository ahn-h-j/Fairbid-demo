package com.cos.fairbid.trade.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 거래 참여자가 아닌 사용자가 접근할 때 발생하는 예외
 * HTTP 403 Forbidden에 매핑
 */
public class NotTradeParticipantException extends DomainException {

    private NotTradeParticipantException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    public static NotTradeParticipantException forTrade(Long tradeId, Long userId) {
        return new NotTradeParticipantException(
                "NOT_TRADE_PARTICIPANT",
                String.format("해당 거래의 참여자가 아닙니다. 거래 ID: %d, 사용자 ID: %d", tradeId, userId)
        );
    }

    public static NotTradeParticipantException sellerOnly(Long tradeId, Long userId) {
        return new NotTradeParticipantException(
                "NOT_TRADE_SELLER",
                String.format("판매자만 접근 가능합니다. 거래 ID: %d, 사용자 ID: %d", tradeId, userId)
        );
    }

    public static NotTradeParticipantException buyerOnly(Long tradeId, Long userId) {
        return new NotTradeParticipantException(
                "NOT_TRADE_BUYER",
                String.format("구매자만 접근 가능합니다. 거래 ID: %d, 사용자 ID: %d", tradeId, userId)
        );
    }

    public static NotTradeParticipantException notParticipant(Long userId, Long tradeId) {
        return forTrade(tradeId, userId);
    }

    public static NotTradeParticipantException notSeller(Long userId, Long tradeId) {
        return sellerOnly(tradeId, userId);
    }

    public static NotTradeParticipantException notBuyer(Long userId, Long tradeId) {
        return buyerOnly(tradeId, userId);
    }
}
