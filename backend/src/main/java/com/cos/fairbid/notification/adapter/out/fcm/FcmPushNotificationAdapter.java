package com.cos.fairbid.notification.adapter.out.fcm;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.notification.application.port.out.NotificationStoragePort;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.notification.domain.InAppNotification;
import com.cos.fairbid.notification.domain.NotificationType;

/**
 * FCM Push 알림 어댑터
 * PushNotificationPort를 구현하여 FCM 전송 수행
 * 메시지 생성은 NotificationType에 위임, 전송은 FcmClient에 위임
 * 동시에 인앱 알림으로 Redis에 저장
 */
@Component
@RequiredArgsConstructor
public class FcmPushNotificationAdapter implements PushNotificationPort {

    private final FcmClient fcmClient;
    private final NotificationStoragePort notificationStoragePort;

    @Override
    public void sendWinningNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        NotificationType type = NotificationType.WINNING;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, bidAmount);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotification(userId, type, title, body, auctionId);
    }

    @Override
    public void sendTransferNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        NotificationType type = NotificationType.TRANSFER;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, bidAmount);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotification(userId, type, title, body, auctionId);
    }

    @Override
    public void sendFailedAuctionNotification(Long sellerId, Long auctionId, String auctionTitle) {
        NotificationType type = NotificationType.FAILED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(sellerId, title, body, type, auctionId);
        saveInAppNotification(sellerId, type, title, body, auctionId);
    }

    @Override
    public void sendResponseReminderNotification(Long buyerId, Long auctionId, String auctionTitle, Long amount) {
        NotificationType type = NotificationType.RESPONSE_REMINDER;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, amount);
        fcmClient.send(buyerId, title, body, type, auctionId);
        saveInAppNotification(buyerId, type, title, body, auctionId);
    }

    @Override
    public void sendSecondRankStandbyNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        NotificationType type = NotificationType.SECOND_RANK_STANDBY;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, bidAmount);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotification(userId, type, title, body, auctionId);
    }

    @Override
    public void sendNoShowPenaltyNotification(Long userId, Long auctionId, String auctionTitle) {
        NotificationType type = NotificationType.NO_SHOW_PENALTY;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotification(userId, type, title, body, auctionId);
    }

    @Override
    public void sendMethodSelectedNotification(
            Long sellerId, Long auctionId, Long tradeId,
            String auctionTitle, boolean isDirect) {
        NotificationType type = NotificationType.METHOD_SELECTED;
        String title = type.getTitle();
        // isDirect를 amount 파라미터로 전달 (1=직거래, 2=택배)
        String body = type.formatBody(auctionTitle, isDirect ? 1L : 2L);
        fcmClient.send(sellerId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(sellerId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendArrangementProposedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.ARRANGEMENT_PROPOSED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(userId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendArrangementCounterProposedNotification(
            Long userId, Long auctionId, Long tradeId,
            String auctionTitle) {
        NotificationType type = NotificationType.ARRANGEMENT_COUNTER_PROPOSED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(userId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendArrangementAcceptedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.ARRANGEMENT_ACCEPTED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(userId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendDeliveryAddressSubmittedNotification(
            Long sellerId, Long auctionId, Long tradeId,
            String auctionTitle) {
        NotificationType type = NotificationType.DELIVERY_ADDRESS_SUBMITTED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(sellerId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(sellerId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendDeliveryShippedNotification(Long buyerId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.DELIVERY_SHIPPED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(buyerId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(buyerId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendTradeCompletedNotification(Long userId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.TRADE_COMPLETED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(userId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(userId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendPaymentConfirmedNotification(Long sellerId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.PAYMENT_CONFIRMED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(sellerId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(sellerId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendPaymentVerifiedNotification(Long buyerId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.PAYMENT_VERIFIED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(buyerId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(buyerId, type, title, body, auctionId, tradeId);
    }

    @Override
    public void sendPaymentRejectedNotification(Long buyerId, Long auctionId, Long tradeId, String auctionTitle) {
        NotificationType type = NotificationType.PAYMENT_REJECTED;
        String title = type.getTitle();
        String body = type.formatBody(auctionTitle, null);
        fcmClient.send(buyerId, title, body, type, auctionId);
        saveInAppNotificationWithTrade(buyerId, type, title, body, auctionId, tradeId);
    }

    /**
     * 인앱 알림을 Redis에 저장한다
     */
    private void saveInAppNotification(Long userId, NotificationType type, String title, String body, Long auctionId) {
        InAppNotification notification = InAppNotification.create(type, title, body, auctionId);
        notificationStoragePort.save(userId, notification);
    }

    /**
     * 거래 관련 인앱 알림을 Redis에 저장한다
     */
    private void saveInAppNotificationWithTrade(
            Long userId, NotificationType type, String title,
            String body, Long auctionId, Long tradeId) {
        InAppNotification notification = InAppNotification.create(type, title, body, auctionId, tradeId);
        notificationStoragePort.save(userId, notification);
    }
}
