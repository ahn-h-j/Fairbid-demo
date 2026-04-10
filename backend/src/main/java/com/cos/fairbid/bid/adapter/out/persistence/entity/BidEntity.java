package com.cos.fairbid.bid.adapter.out.persistence.entity;

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

import com.cos.fairbid.bid.domain.BidType;

/**
 * 입찰 JPA 엔티티
 * DB 테이블 매핑 전용 (비즈니스 로직 금지)
 */
@Entity
@Table(name = "bid")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class BidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "bidder_id", nullable = false)
    private Long bidderId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "bid_type", nullable = false)
    private BidType bidType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Redis Stream 메시지 ID (멱등성 보장용, at-least-once 중복 방지) */
    @Column(name = "stream_record_id", unique = true)
    private String streamRecordId;

    @Builder
    private BidEntity(
            Long id,
            Long auctionId,
            Long bidderId,
            Long amount,
            BidType bidType,
            String streamRecordId
    ) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.bidType = bidType;
        this.streamRecordId = streamRecordId;
    }
}
