package com.cos.fairbid.notification.adapter.out.fcm;

import org.springframework.stereotype.Component;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.notification.domain.NotificationType;

/**
 * FCM 전송 클라이언트
 * Firebase Cloud Messaging 전송 책임만 담당
 *
 * TODO: User 도메인에서 FCM 토큰 관리 구현 필요
 */
@Slf4j
@Component
public class FcmClient {

    /**
     * FCM Push 알림 전송
     *
     * @param userId    사용자 ID
     * @param title     알림 제목
     * @param body      알림 본문
     * @param type      알림 유형
     * @param auctionId 경매 ID
     */
    public void send(Long userId, String title, String body, NotificationType type, Long auctionId) {
        if (!isFirebaseInitialized()) {
            logMock(userId, type, title, body);
            return;
        }

        String fcmToken = getFcmToken(userId);
        if (fcmToken == null) {
            log.warn("FCM 토큰이 없어 Push 알림을 보낼 수 없습니다. userId={}", userId);
            logMock(userId, type, title, body);
            return;
        }

        sendToFirebase(userId, fcmToken, title, body, type, auctionId);
    }

    private boolean isFirebaseInitialized() {
        return !FirebaseApp.getApps().isEmpty();
    }

    /**
     * TODO: User 도메인에서 FCM 토큰 조회 구현 필요
     */
    private String getFcmToken(Long userId) {
        return null; // 임시
    }

    private void sendToFirebase(Long userId, String fcmToken, String title, String body,
                                NotificationType type, Long auctionId) {
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", type.name())
                    .putData("auctionId", String.valueOf(auctionId))
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공 - userId={}, messageId={}", userId, response);
        } catch (Exception e) {
            log.error("FCM 전송 실패 - userId={}", userId, e);
        }
    }

    private void logMock(Long userId, NotificationType type, String title, String body) {
        log.info("[FCM Mock] userId={}, type={}, title={}, body={}", userId, type.name(), title, body);
    }
}
