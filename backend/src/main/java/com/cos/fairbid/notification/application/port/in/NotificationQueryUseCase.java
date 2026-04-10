package com.cos.fairbid.notification.application.port.in;

import java.util.List;

import com.cos.fairbid.notification.domain.InAppNotification;

/**
 * 알림 조회 UseCase
 * 사용자의 알림 목록 조회, 읽음 처리, 카운트 조회를 담당
 */
public interface NotificationQueryUseCase {

    /**
     * 사용자의 알림 목록을 조회한다
     *
     * @param userId 사용자 ID
     * @return 알림 목록 (최신순, 최대 50개)
     */
    List<InAppNotification> getNotifications(Long userId);

    /**
     * 읽지 않은 알림 개수를 조회한다
     *
     * @param userId 사용자 ID
     * @return 읽지 않은 알림 개수
     */
    int countUnread(Long userId);

    /**
     * 알림을 읽음 처리한다
     *
     * @param userId         사용자 ID
     * @param notificationId 알림 ID
     */
    void markAsRead(Long userId, String notificationId);
}
