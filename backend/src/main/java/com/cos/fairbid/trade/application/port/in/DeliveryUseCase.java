package com.cos.fairbid.trade.application.port.in;

import java.util.Optional;

import com.cos.fairbid.trade.domain.DeliveryInfo;

/**
 * 택배 유스케이스
 * 택배 배송 관련 인바운드 포트
 */
public interface DeliveryUseCase {

    /**
     * 배송 정보를 조회한다
     *
     * @param tradeId 거래 ID
     * @return 배송 정보 (Optional)
     */
    Optional<DeliveryInfo> findByTradeId(Long tradeId);

    /**
     * 배송지 정보를 입력한다 (구매자)
     *
     * @param tradeId        거래 ID
     * @param userId         요청자 ID (구매자)
     * @param recipientName  수령인 이름
     * @param recipientPhone 수령인 연락처
     * @param postalCode     우편번호
     * @param address        주소
     * @param addressDetail  상세주소
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 구매자가 아닌 경우
     * @throws com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException 택배 거래가 아닌 경우
     */
    DeliveryInfo submitAddress(
            Long tradeId,
            Long userId,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    );

    /**
     * 발송 정보를 입력한다 (판매자)
     *
     * @param tradeId        거래 ID
     * @param userId         요청자 ID (판매자)
     * @param courierCompany 택배사
     * @param trackingNumber 송장번호
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 판매자가 아닌 경우
     */
    DeliveryInfo ship(Long tradeId, Long userId, String courierCompany, String trackingNumber);

    /**
     * 수령을 확인한다 (구매자)
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (구매자)
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 구매자가 아닌 경우
     */
    DeliveryInfo confirmDelivery(Long tradeId, Long userId);

    /**
     * 입금 완료를 확인한다 (구매자)
     * 구매자가 판매자 계좌로 입금 완료 후 호출한다.
     * 판매자에게 입금 완료 알림이 발송된다.
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (구매자)
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 구매자가 아닌 경우
     */
    DeliveryInfo confirmPayment(Long tradeId, Long userId);

    /**
     * 입금을 확인한다 (판매자)
     * 구매자가 입금 완료를 알린 후, 판매자가 실제 입금을 확인할 때 호출한다.
     * 입금 확인 후에만 발송(ship)이 가능하다.
     * 구매자에게 입금 확인 알림이 발송된다.
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (판매자)
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 판매자가 아닌 경우
     */
    DeliveryInfo verifyPayment(Long tradeId, Long userId);

    /**
     * 입금을 거절한다 (판매자)
     * 구매자가 입금 완료를 알렸지만 실제로 입금되지 않은 경우 호출한다.
     * paymentConfirmed가 false로 되돌아가며, 구매자에게 미입금 알림이 발송된다.
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (판매자)
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 판매자가 아닌 경우
     */
    DeliveryInfo rejectPayment(Long tradeId, Long userId);
}
