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
import com.cos.fairbid.trade.adapter.in.dto.DirectTradeInfoResponse;
import com.cos.fairbid.trade.adapter.in.dto.DirectTradeProposalRequest;
import com.cos.fairbid.trade.application.port.in.DirectTradeUseCase;
import com.cos.fairbid.trade.domain.DirectTradeInfo;

/**
 * 직거래 API 컨트롤러
 *
 * - POST /api/v1/trades/{tradeId}/direct/propose - 시간 제안 (판매자)
 * - POST /api/v1/trades/{tradeId}/direct/accept - 수락
 * - POST /api/v1/trades/{tradeId}/direct/counter - 역제안
 */
@RestController
@RequestMapping("/api/v1/trades/{tradeId}/direct")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class DirectTradeController {

    private final DirectTradeUseCase directTradeUseCase;

    /**
     * 직거래 시간 제안 (판매자 첫 제안)
     */
    @PostMapping("/propose")
    public ResponseEntity<ApiResponse<DirectTradeInfoResponse>> propose(
            @PathVariable Long tradeId,
            @Valid @RequestBody DirectTradeProposalRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DirectTradeInfo info = directTradeUseCase.propose(
                tradeId,
                userId,
                request.getMeetingDate(),
                request.getMeetingTime()
        );
        return ResponseEntity.ok(ApiResponse.success(DirectTradeInfoResponse.from(info)));
    }

    /**
     * 제안 수락 (약속 확정)
     */
    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<DirectTradeInfoResponse>> accept(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DirectTradeInfo info = directTradeUseCase.accept(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(DirectTradeInfoResponse.from(info)));
    }

    /**
     * 역제안
     */
    @PostMapping("/counter")
    public ResponseEntity<ApiResponse<DirectTradeInfoResponse>> counterPropose(
            @PathVariable Long tradeId,
            @Valid @RequestBody DirectTradeProposalRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DirectTradeInfo info = directTradeUseCase.counterPropose(
                tradeId,
                userId,
                request.getMeetingDate(),
                request.getMeetingTime()
        );
        return ResponseEntity.ok(ApiResponse.success(DirectTradeInfoResponse.from(info)));
    }
}
