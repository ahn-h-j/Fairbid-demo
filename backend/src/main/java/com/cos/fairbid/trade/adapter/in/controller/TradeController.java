package com.cos.fairbid.trade.adapter.in.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.trade.adapter.in.dto.SelectMethodRequest;
import com.cos.fairbid.trade.adapter.in.dto.TradeDetailResponse;
import com.cos.fairbid.trade.adapter.in.dto.TradeResponse;
import com.cos.fairbid.trade.application.port.in.DeliveryUseCase;
import com.cos.fairbid.trade.application.port.in.DirectTradeUseCase;
import com.cos.fairbid.trade.application.port.in.TradeCommandUseCase;
import com.cos.fairbid.trade.application.port.in.TradeQueryUseCase;
import com.cos.fairbid.trade.domain.DeliveryInfo;
import com.cos.fairbid.trade.domain.DeliveryStatus;
import com.cos.fairbid.trade.domain.DirectTradeInfo;
import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.exception.NotTradeParticipantException;
import com.cos.fairbid.user.application.port.in.GetMyProfileUseCase;
import com.cos.fairbid.user.domain.User;

/**
 * 거래 API 컨트롤러
 *
 * 거래 조회:
 * - GET /api/v1/trades/{tradeId} - 거래 상세 조회
 * - GET /api/v1/trades/my - 내 거래 목록
 *
 * 거래 명령:
 * - POST /api/v1/trades/{tradeId}/method - 거래 방식 선택
 * - POST /api/v1/trades/{tradeId}/complete - 거래 완료
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class TradeController {

    private final TradeQueryUseCase tradeQueryUseCase;
    private final TradeCommandUseCase tradeCommandUseCase;
    private final DirectTradeUseCase directTradeUseCase;
    private final DeliveryUseCase deliveryUseCase;
    private final GetMyProfileUseCase getMyProfileUseCase;

    /**
     * 거래 상세 조회
     * 거래 참여자(판매자/구매자)만 조회 가능
     */
    @GetMapping("/{tradeId}")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> getTrade(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Trade trade = tradeQueryUseCase.getById(tradeId);

        // 거래 참여자 권한 검사
        if (!trade.isParticipant(userId)) {
            throw NotTradeParticipantException.notParticipant(userId, tradeId);
        }

        // 거래 방식에 따라 상세 정보 조회
        DirectTradeInfo directTradeInfo = null;
        DeliveryInfo deliveryInfo = null;

        if (trade.isDirectTrade()) {
            directTradeInfo = directTradeUseCase.findByTradeId(tradeId).orElse(null);
        } else if (trade.isDelivery()) {
            deliveryInfo = deliveryUseCase.findByTradeId(tradeId).orElse(null);
        }

        // 택배 거래 시 구매자에게 판매자 계좌 정보 노출 (입금 대기 상태일 때)
        TradeDetailResponse.SellerBankAccount sellerBankAccount = null;
        if (trade.isDelivery() && trade.isBuyer(userId) && deliveryInfo != null
                && deliveryInfo.getStatus() == DeliveryStatus.AWAITING_PAYMENT) {
            User seller = getMyProfileUseCase.getMyProfile(trade.getSellerId());
            if (seller.hasBankAccount()) {
                sellerBankAccount = TradeDetailResponse.SellerBankAccount.builder()
                        .bankName(seller.getBankName())
                        .accountNumber(seller.getAccountNumber())
                        .accountHolder(seller.getAccountHolder())
                        .build();
            }
        }

        TradeDetailResponse response = TradeDetailResponse.from(
                trade, directTradeInfo, deliveryInfo, sellerBankAccount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 내 거래 목록 조회
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TradeResponse>>> getMyTrades() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Trade> trades = tradeQueryUseCase.findByUserId(userId);
        List<TradeResponse> response = trades.stream()
                .map(TradeResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 거래 방식 선택 (둘 다 가능한 경우 구매자가 호출)
     */
    @PostMapping("/{tradeId}/method")
    public ResponseEntity<ApiResponse<TradeResponse>> selectMethod(
            @PathVariable Long tradeId,
            @Valid @RequestBody SelectMethodRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Trade trade = tradeCommandUseCase.selectMethod(tradeId, userId, request.getMethod());
        return ResponseEntity.ok(ApiResponse.success(TradeResponse.from(trade)));
    }

    /**
     * 거래 완료
     */
    @PostMapping("/{tradeId}/complete")
    public ResponseEntity<ApiResponse<TradeResponse>> complete(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        Trade trade = tradeCommandUseCase.complete(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(TradeResponse.from(trade)));
    }
}
