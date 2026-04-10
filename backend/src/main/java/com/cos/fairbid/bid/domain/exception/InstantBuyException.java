package com.cos.fairbid.bid.domain.exception;

import org.springframework.http.HttpStatus;

import com.cos.fairbid.common.exception.DomainException;

/**
 * 즉시 구매 관련 예외
 * HTTP 400 Bad Request에 매핑
 */
public class InstantBuyException extends DomainException {

    private InstantBuyException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 즉시 구매가가 설정되지 않은 경매인 경우
     *
     * @param auctionId 경매 ID
     * @return InstantBuyException 인스턴스
     */
    public static InstantBuyException notAvailable(Long auctionId) {
        return new InstantBuyException(
                "INSTANT_BUY_NOT_AVAILABLE",
                String.format("경매 %d는 즉시 구매가 설정되지 않았습니다.", auctionId)
        );
    }

    /**
     * 현재가가 즉시 구매가의 90% 이상이라 즉시 구매가 비활성화된 경우
     *
     * @param auctionId 경매 ID
     * @return InstantBuyException 인스턴스
     */
    public static InstantBuyException disabled(Long auctionId) {
        return new InstantBuyException(
                "INSTANT_BUY_DISABLED",
                String.format("경매 %d의 현재가가 즉시 구매가의 90%%를 넘어 즉시 구매가 비활성화되었습니다.", auctionId)
        );
    }

    /**
     * 이미 즉시 구매가 진행 중인 경우
     *
     * @param auctionId 경매 ID
     * @return InstantBuyException 인스턴스
     */
    public static InstantBuyException alreadyActivated(Long auctionId) {
        return new InstantBuyException(
                "INSTANT_BUY_ALREADY_ACTIVATED",
                String.format("경매 %d는 이미 즉시 구매가 진행 중입니다.", auctionId)
        );
    }
}
