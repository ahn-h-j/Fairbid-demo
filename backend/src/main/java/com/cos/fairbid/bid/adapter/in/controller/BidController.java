package com.cos.fairbid.bid.adapter.in.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.bid.adapter.in.dto.BidResponse;
import com.cos.fairbid.bid.adapter.in.dto.PlaceBidRequest;
import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.common.annotation.RequireOnboarding;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;

/**
 * 입찰 REST Controller
 */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class BidController {

    private final PlaceBidUseCase placeBidUseCase;

    /**
     * 입찰 API
     * 온보딩 완료한 사용자만 입찰할 수 있다.
     *
     * @param auctionId 경매 ID
     * @param request   입찰 요청
     * @return 생성된 입찰 정보
     */
    @PostMapping
    @RequireOnboarding
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @PathVariable Long auctionId,
            @Valid @RequestBody PlaceBidRequest request
    ) {
        Long bidderId = SecurityUtils.getCurrentUserId();

        Bid bid = placeBidUseCase.placeBid(request.toCommand(auctionId, bidderId));
        BidResponse response = BidResponse.from(bid);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
