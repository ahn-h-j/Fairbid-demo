package com.cos.fairbid.trade.adapter.in.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.trade.domain.DirectTradeInfo;
import com.cos.fairbid.trade.domain.DirectTradeStatus;

/**
 * 직거래 정보 응답 DTO
 */
@Getter
@Builder
public class DirectTradeInfoResponse {

    private Long id;
    private Long tradeId;
    private String location;
    private LocalDate meetingDate;
    private LocalTime meetingTime;
    private DirectTradeStatus status;
    private Long proposedBy;

    public static DirectTradeInfoResponse from(DirectTradeInfo info) {
        return DirectTradeInfoResponse.builder()
                .id(info.getId())
                .tradeId(info.getTradeId())
                .location(info.getLocation())
                .meetingDate(info.getMeetingDate())
                .meetingTime(info.getMeetingTime())
                .status(info.getStatus())
                .proposedBy(info.getProposedBy())
                .build();
    }
}
