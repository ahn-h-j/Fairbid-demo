package com.cos.fairbid.notification.application.port.out;

import java.util.List;

import com.cos.fairbid.notification.domain.InAppNotification;

/**
 * 인앱 알림 저장소 아웃바운드 포트
 * Redis에 알림을 저장하고 조회한다.
 */
public interface NotificationStoragePort {

    /**
     * 알림을 저장한다 (TTL 24시간)
     *
     * @param userId       사용자 ID
     * @param notification 알림 정보
     */
    void save(Long userId, InAppNotification notification);

    /**
     * 사용자의 알림 목록을 조회한다
     *
     * @param userId 사용자 ID
     * @return 알림 목록 (최신순)
     */
    List<InAppNotification> findByUserId(Long userId);

    /**
     * 알림을 읽음 처리한다
     *
     * @param userId         사용자 ID
     * @param notificationId 알림 ID
     */
    void markAsRead(Long userId, String notificationId);

    /**
     * 사용자의 읽지 않은 알림 개수를 조회한다
     *
     * @param userId 사용자 ID
     * @return 읽지 않은 알림 개수
     */
    int countUnread(Long userId);
}
