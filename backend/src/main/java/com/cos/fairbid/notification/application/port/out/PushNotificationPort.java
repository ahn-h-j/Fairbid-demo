package com.cos.fairbid.notification.application.port.out;

/**
 * Push 알림 발송 아웃바운드 포트
 */
public interface PushNotificationPort {

    /**
     * 낙찰 알림을 발송한다
     *
     * @param userId      사용자 ID
     * @param auctionId   경매 ID
     * @param auctionTitle 경매 제목
     * @param bidAmount   낙찰 금액
     */
    void sendWinningNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount);

    /**
     * 2순위 승계 알림을 발송한다
     *
     * @param userId      사용자 ID
     * @param auctionId   경매 ID
     * @param auctionTitle 경매 제목
     * @param bidAmount   입찰 금액
     */
    void sendTransferNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount);

    /**
     * 유찰 알림을 발송한다 (판매자에게)
     *
     * @param sellerId     판매자 ID
     * @param auctionId    경매 ID
     * @param auctionTitle 경매 제목
     */
    void sendFailedAuctionNotification(Long sellerId, Long auctionId, String auctionTitle);

    /**
     * 응답 리마인더 알림을 발송한다
     * 응답 마감 12시간 전에 구매자에게 발송
     *
     * @param buyerId      구매자 ID
     * @param auctionId    경매 ID
     * @param auctionTitle 경매 제목
     * @param amount       거래 금액
     */
    void sendResponseReminderNotification(Long buyerId, Long auctionId, String auctionTitle, Long amount);

    /**
     * 2순위 대기 알림을 발송한다
     * 경매 종료 시 2순위 후보에게 발송
     *
     * @param userId       사용자 ID
     * @param auctionId    경매 ID
     * @param auctionTitle 경매 제목
     * @param bidAmount    입찰 금액
     */
    void sendSecondRankStandbyNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount);

    /**
     * 노쇼 처리 알림을 발송한다 (노쇼 당한 사람에게)
     *
     * @param userId       사용자 ID
     * @param auctionId    경매 ID
     * @param auctionTitle 경매 제목
     */
    void sendNoShowPenaltyNotification(Long userId, Long auctionId, String auctionTitle);

    /**
     * 거래 방식 선택 알림을 발송한다 (판매자에게)
     *
     * @param sellerId     판매자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     * @param isDirect     직거래 여부 (true: 직거래, false: 택배)
     */
    void sendMethodSelectedNotification(
            Long sellerId, Long auctionId, Long tradeId,
            String auctionTitle, boolean isDirect);

    /**
     * 거래 일정 제안 알림을 발송한다
     *
     * @param userId       수신자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendArrangementProposedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 거래 일정 역제안 알림을 발송한다
     *
     * @param userId       수신자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendArrangementCounterProposedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 거래 일정 수락 알림을 발송한다
     *
     * @param userId       수신자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendArrangementAcceptedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 배송지 입력 알림을 발송한다 (판매자에게)
     *
     * @param sellerId     판매자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendDeliveryAddressSubmittedNotification(Long sellerId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 발송 완료 알림을 발송한다 (구매자에게)
     *
     * @param buyerId      구매자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendDeliveryShippedNotification(Long buyerId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 거래 완료 알림을 발송한다
     *
     * @param userId       수신자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendTradeCompletedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 입금 완료 알림을 발송한다 (판매자에게)
     * 구매자가 입금 완료를 확인하면 판매자에게 발송한다.
     *
     * @param sellerId     판매자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendPaymentConfirmedNotification(Long sellerId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 입금 확인 알림을 발송한다 (구매자에게)
     * 판매자가 입금을 확인하면 구매자에게 발송한다.
     *
     * @param buyerId      구매자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendPaymentVerifiedNotification(Long buyerId, Long auctionId, Long tradeId, String auctionTitle);

    /**
     * 미입금 알림을 발송한다 (구매자에게)
     * 판매자가 입금을 확인하지 못하면 구매자에게 발송한다.
     *
     * @param buyerId      구매자 ID
     * @param auctionId    경매 ID
     * @param tradeId      거래 ID
     * @param auctionTitle 경매 제목
     */
    void sendPaymentRejectedNotification(Long buyerId, Long auctionId, Long tradeId, String auctionTitle);
}
