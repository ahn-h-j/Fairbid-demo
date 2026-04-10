package com.cos.fairbid.trade.adapter.in.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 직거래 시간 제안/역제안 요청 DTO
 */
@Getter
public class DirectTradeProposalRequest {

    @NotNull(message = "만남 날짜는 필수입니다.")
    @FutureOrPresent(message = "만남 날짜는 오늘 이후여야 합니다.")
    private LocalDate meetingDate;

    @NotNull(message = "만남 시간은 필수입니다.")
    private LocalTime meetingTime;
}
