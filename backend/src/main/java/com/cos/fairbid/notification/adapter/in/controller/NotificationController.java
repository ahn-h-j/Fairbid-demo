package com.cos.fairbid.notification.adapter.in.controller;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.notification.application.port.in.NotificationQueryUseCase;
import com.cos.fairbid.notification.domain.InAppNotification;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 알림 REST Controller
 *
 * 엔드포인트:
 * - GET  /api/v1/notifications       → 내 알림 목록 조회
 * - GET  /api/v1/notifications/count → 읽지 않은 알림 개수
 * - POST /api/v1/notifications/{id}/read → 알림 읽음 처리
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
@EnabledOnRole({"api", "all"})
public class NotificationController {

    private final NotificationQueryUseCase notificationQueryUseCase;

    /**
     * 내 알림 목록을 조회한다
     *
     * @return 알림 목록 (최신순, 최대 50개)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<InAppNotification> notifications = notificationQueryUseCase.getNotifications(userId);

        List<NotificationResponse> response = notifications.stream()
                .map(NotificationResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 읽지 않은 알림 개수를 조회한다
     *
     * @return 읽지 않은 알림 개수
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        int count = notificationQueryUseCase.countUnread(userId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count)));
    }

    /**
     * 알림을 읽음 처리한다
     *
     * @param notificationId 알림 ID
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable @NotBlank String notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationQueryUseCase.markAsRead(userId, notificationId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 알림 응답 DTO
     */
    public record NotificationResponse(
            String id,
            String type,
            String title,
            String body,
            Long auctionId,
            Long tradeId,
            boolean read,
            LocalDateTime createdAt
    ) {
        public static NotificationResponse from(InAppNotification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType().name(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getAuctionId(),
                    notification.getTradeId(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }
}
