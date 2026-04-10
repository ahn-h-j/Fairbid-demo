package com.cos.fairbid.trade.adapter.in.controller;

import org.springframework.http.ResponseEntity;
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
import com.cos.fairbid.trade.adapter.in.dto.AddressRequest;
import com.cos.fairbid.trade.adapter.in.dto.DeliveryInfoResponse;
import com.cos.fairbid.trade.adapter.in.dto.ShippingRequest;
import com.cos.fairbid.trade.application.port.in.DeliveryUseCase;
import com.cos.fairbid.trade.domain.DeliveryInfo;

/**
 * 택배 배송 API 컨트롤러
 *
 * - POST /api/v1/trades/{tradeId}/delivery/address - 배송지 입력 (구매자)
 * - POST /api/v1/trades/{tradeId}/delivery/payment - 입금 완료 확인 (구매자)
 * - POST /api/v1/trades/{tradeId}/delivery/payment/verify - 입금 확인 (판매자)
 * - POST /api/v1/trades/{tradeId}/delivery/payment/reject - 미입금 처리 (판매자)
 * - POST /api/v1/trades/{tradeId}/delivery/ship - 송장 입력 (판매자)
 * - POST /api/v1/trades/{tradeId}/delivery/confirm - 수령 확인 (구매자)
 */
@RestController
@RequestMapping("/api/v1/trades/{tradeId}/delivery")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class DeliveryController {

    private final DeliveryUseCase deliveryUseCase;

    /**
     * 배송지 입력 (구매자)
     */
    @PostMapping("/address")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> submitAddress(
            @PathVariable Long tradeId,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.submitAddress(
                tradeId,
                userId,
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getPostalCode(),
                request.getAddress(),
                request.getAddressDetail()
        );
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 입금 완료 확인 (구매자)
     * 구매자가 판매자 계좌로 입금 후 호출한다.
     */
    @PostMapping("/payment")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> confirmPayment(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.confirmPayment(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 입금 확인 (판매자)
     * 판매자가 구매자의 입금을 확인한다.
     */
    @PostMapping("/payment/verify")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> verifyPayment(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.verifyPayment(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 미입금 처리 (판매자)
     * 판매자가 구매자의 입금을 확인하지 못한 경우 호출한다.
     */
    @PostMapping("/payment/reject")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> rejectPayment(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.rejectPayment(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 송장 입력 (판매자)
     */
    @PostMapping("/ship")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> ship(
            @PathVariable Long tradeId,
            @Valid @RequestBody ShippingRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.ship(
                tradeId,
                userId,
                request.getCourierCompany(),
                request.getTrackingNumber()
        );
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 수령 확인 (구매자)
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> confirmDelivery(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.confirmDelivery(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }
}
