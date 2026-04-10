package com.cos.fairbid.notification.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.notification.application.port.in.NotificationQueryUseCase;
import com.cos.fairbid.notification.application.port.out.NotificationStoragePort;
import com.cos.fairbid.notification.domain.InAppNotification;

/**
 * 알림 서비스
 * 알림 조회 및 읽음 처리 비즈니스 로직
 */
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationQueryUseCase {

    private final NotificationStoragePort notificationStoragePort;

    @Override
    public List<InAppNotification> getNotifications(Long userId) {
        return notificationStoragePort.findByUserId(userId);
    }

    @Override
    public int countUnread(Long userId) {
        return notificationStoragePort.countUnread(userId);
    }

    @Override
    public void markAsRead(Long userId, String notificationId) {
        notificationStoragePort.markAsRead(userId, notificationId);
    }
}
