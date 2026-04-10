package com.cos.fairbid.trade.adapter.in.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.trade.domain.DeliveryInfo;
import com.cos.fairbid.trade.domain.DirectTradeInfo;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;
import com.cos.fairbid.trade.domain.TradeStatus;

/**
 * 거래 상세 응답 DTO (직거래/택배 정보 포함)
 */
@Getter
@Builder
public class TradeDetailResponse {

    private Long id;
    private Long auctionId;
    private Long sellerId;
    private Long buyerId;
    private Long finalPrice;
    private TradeStatus status;
    private TradeMethod method;
    private LocalDateTime responseDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // 직거래 정보 (직거래인 경우)
    private DirectTradeInfoResponse directTradeInfo;

    // 배송 정보 (택배인 경우)
    private DeliveryInfoResponse deliveryInfo;

    // 판매자 계좌 정보 (택배 거래 시 입금용, 구매자에게만 노출)
    private SellerBankAccount sellerBankAccount;

    @Getter
    @Builder
    public static class SellerBankAccount {
        private String bankName;
        private String accountNumber;
        private String accountHolder;
    }

    /**
     * 판매자 계좌 없이 생성 (기본)
     */
    public static TradeDetailResponse from(Trade trade, DirectTradeInfo directTradeInfo, DeliveryInfo deliveryInfo) {
        return from(trade, directTradeInfo, deliveryInfo, null);
    }

    /**
     * 판매자 계좌 정보 포함하여 생성
     */
    public static TradeDetailResponse from(Trade trade, DirectTradeInfo directTradeInfo, DeliveryInfo deliveryInfo,
                                           SellerBankAccount sellerBankAccount) {
        return TradeDetailResponse.builder()
                .id(trade.getId())
                .auctionId(trade.getAuctionId())
                .sellerId(trade.getSellerId())
                .buyerId(trade.getBuyerId())
                .finalPrice(trade.getFinalPrice())
                .status(trade.getStatus())
                .method(trade.getMethod())
                .responseDeadline(trade.getResponseDeadline())
                .createdAt(trade.getCreatedAt())
                .completedAt(trade.getCompletedAt())
                .directTradeInfo(directTradeInfo != null ? DirectTradeInfoResponse.from(directTradeInfo) : null)
                .deliveryInfo(deliveryInfo != null ? DeliveryInfoResponse.from(deliveryInfo) : null)
                .sellerBankAccount(sellerBankAccount)
                .build();
    }
}
