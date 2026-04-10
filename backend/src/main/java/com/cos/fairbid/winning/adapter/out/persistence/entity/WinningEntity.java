package com.cos.fairbid.winning.adapter.out.persistence.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.cos.fairbid.winning.domain.WinningStatus;

/**
 * 낙찰 JPA 엔티티
 * DB 테이블 매핑 전용 (비즈니스 로직 금지)
 */
@Entity
@Table(name = "winning")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WinningEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Column(name = "bidder_id", nullable = false)
    private Long bidderId;

    @Column(name = "bid_amount", nullable = false)
    private Long bidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WinningStatus status;

    // 컬럼명은 payment_deadline 유지 (DB 호환성), 필드명은 responseDeadline
    @Column(name = "payment_deadline")
    private LocalDateTime responseDeadline;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private WinningEntity(
            Long id,
            Long auctionId,
            Integer rank,
            Long bidderId,
            Long bidAmount,
            WinningStatus status,
            LocalDateTime responseDeadline
    ) {
        this.id = id;
        this.auctionId = auctionId;
        this.rank = rank;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.status = status;
        this.responseDeadline = responseDeadline;
    }
}
