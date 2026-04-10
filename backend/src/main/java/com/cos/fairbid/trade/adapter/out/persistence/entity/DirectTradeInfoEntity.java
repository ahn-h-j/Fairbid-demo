package com.cos.fairbid.trade.adapter.out.persistence.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import com.cos.fairbid.trade.domain.DirectTradeStatus;

/**
 * 직거래 정보 JPA 엔티티
 * DB 테이블 매핑 전용 (비즈니스 로직 금지)
 */
@Entity
@Table(name = "direct_trade_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectTradeInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private String location;

    @Column(name = "meeting_date")
    private LocalDate meetingDate;

    @Column(name = "meeting_time")
    private LocalTime meetingTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DirectTradeStatus status;

    @Column(name = "proposed_by")
    private Long proposedBy;

    @Builder
    private DirectTradeInfoEntity(
            Long id,
            Long tradeId,
            String location,
            LocalDate meetingDate,
            LocalTime meetingTime,
            DirectTradeStatus status,
            Long proposedBy
    ) {
        this.id = id;
        this.tradeId = tradeId;
        this.location = location;
        this.meetingDate = meetingDate;
        this.meetingTime = meetingTime;
        this.status = status;
        this.proposedBy = proposedBy;
    }
}
