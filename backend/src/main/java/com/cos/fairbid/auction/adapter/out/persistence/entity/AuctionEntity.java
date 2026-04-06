package com.cos.fairbid.auction.adapter.out.persistence.entity;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 경매 JPA 엔티티
 * DB 테이블 매핑 전용 (비즈니스 로직 금지)
 */
@Entity
@Table(name = "auction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AuctionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "start_price", nullable = false)
    private Long startPrice;

    @Column(name = "current_price", nullable = false)
    private Long currentPrice;

    @Column(name = "instant_buy_price")
    private Long instantBuyPrice;

    @Column(name = "bid_increment", nullable = false)
    private Long bidIncrement;

    @Column(name = "scheduled_end_time", nullable = false)
    private LocalDateTime scheduledEndTime;

    @Column(name = "actual_end_time")
    private LocalDateTime actualEndTime;

    @Column(name = "extension_count", nullable = false)
    private Integer extensionCount;

    @Column(name = "total_bid_count", nullable = false)
    private Integer totalBidCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(name = "winner_id")
    private Long winnerId;

    // 즉시 구매 관련 필드
    @Column(name = "instant_buyer_id")
    private Long instantBuyerId;

    @Column(name = "instant_buy_activated_time")
    private LocalDateTime instantBuyActivatedTime;

    // 거래 방식 관련 필드
    @Column(name = "direct_trade_available", nullable = false)
    private Boolean directTradeAvailable;

    @Column(name = "delivery_available", nullable = false)
    private Boolean deliveryAvailable;

    @Column(name = "direct_trade_location")
    private String directTradeLocation;

    // 이미지 URL 목록 (별도 테이블로 저장)
    @ElementCollection
    @CollectionTable(name = "auction_image", joinColumns = @JoinColumn(name = "auction_id"))
    @Column(name = "image_url", length = 2048)
    @OrderColumn(name = "image_order")
    private List<String> imageUrls;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private AuctionEntity(
            Long id,
            Long sellerId,
            String title,
            String description,
            Category category,
            Long startPrice,
            Long currentPrice,
            Long instantBuyPrice,
            Long bidIncrement,
            LocalDateTime scheduledEndTime,
            LocalDateTime actualEndTime,
            Integer extensionCount,
            Integer totalBidCount,
            AuctionStatus status,
            Long winnerId,
            Long instantBuyerId,
            LocalDateTime instantBuyActivatedTime,
            Boolean directTradeAvailable,
            Boolean deliveryAvailable,
            String directTradeLocation,
            List<String> imageUrls,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.startPrice = startPrice;
        this.currentPrice = currentPrice;
        this.instantBuyPrice = instantBuyPrice;
        this.bidIncrement = bidIncrement;
        this.scheduledEndTime = scheduledEndTime;
        this.actualEndTime = actualEndTime;
        this.extensionCount = extensionCount;
        this.totalBidCount = totalBidCount;
        this.status = status;
        this.winnerId = winnerId;
        this.instantBuyerId = instantBuyerId;
        this.instantBuyActivatedTime = instantBuyActivatedTime;
        this.directTradeAvailable = directTradeAvailable;
        this.deliveryAvailable = deliveryAvailable;
        this.directTradeLocation = directTradeLocation;
        this.imageUrls = imageUrls;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
