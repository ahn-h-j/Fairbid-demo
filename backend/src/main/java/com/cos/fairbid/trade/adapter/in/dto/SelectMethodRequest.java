package com.cos.fairbid.trade.adapter.in.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import com.cos.fairbid.trade.domain.TradeMethod;

/**
 * 거래 방식 선택 요청 DTO
 */
@Getter
public class SelectMethodRequest {

    @NotNull(message = "거래 방식은 필수입니다.")
    private TradeMethod method;
}
