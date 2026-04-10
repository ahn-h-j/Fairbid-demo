package com.cos.fairbid.notification.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * 인앱 알림 도메인 객체
 * Redis에 저장되어 24시간 후 자동 삭제된다.
 */
@Getter
@Builder
public class InAppNotification {

    /** 알림 고유 ID */
    private String id;

    /** 알림 유형 */
    private NotificationType type;

    /** 제목 */
    private String title;

    /** 본문 */
    private String body;

    /** 관련 경매 ID */
    private Long auctionId;

    /** 관련 거래 ID (거래 관련 알림용) */
    private Long tradeId;

    /** 읽음 여부 */
    private boolean read;

    /** 생성 시각 */
    private LocalDateTime createdAt;

    /**
     * 새 알림을 생성한다 (경매 관련)
     */
    public static InAppNotification create(NotificationType type, String title, String body, Long auctionId) {
        return create(type, title, body, auctionId, null);
    }

    /**
     * 새 알림을 생성한다 (거래 관련)
     */
    public static InAppNotification create(
            NotificationType type, String title, String body,
            Long auctionId, Long tradeId) {
        return InAppNotification.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .title(title)
                .body(body)
                .auctionId(auctionId)
                .tradeId(tradeId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 읽음 처리된 알림을 반환한다
     */
    public InAppNotification markAsRead() {
        return InAppNotification.builder()
                .id(this.id)
                .type(this.type)
                .title(this.title)
                .body(this.body)
                .auctionId(this.auctionId)
                .tradeId(this.tradeId)
                .read(true)
                .createdAt(this.createdAt)
                .build();
    }
}
