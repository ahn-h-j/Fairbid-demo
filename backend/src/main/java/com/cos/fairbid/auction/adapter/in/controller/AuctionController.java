package com.cos.fairbid.auction.adapter.in.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.cos.fairbid.auction.adapter.in.dto.AuctionListResponse;
import com.cos.fairbid.auction.adapter.in.dto.AuctionResponse;
import com.cos.fairbid.auction.adapter.in.dto.CreateAuctionRequest;
import com.cos.fairbid.auction.application.port.in.CreateAuctionUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionDetailUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionListUseCase;
import com.cos.fairbid.auction.application.port.in.GetUserWinningInfoUseCase;
import com.cos.fairbid.auction.application.port.in.GetUserWinningInfoUseCase.UserWinningInfo;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.annotation.RequireOnboarding;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;

/**
 * 경매 REST Controller
 */
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class AuctionController {

    private final CreateAuctionUseCase createAuctionUseCase;
    private final GetAuctionDetailUseCase getAuctionDetailUseCase;
    private final GetAuctionListUseCase getAuctionListUseCase;
    private final GetUserWinningInfoUseCase getUserWinningInfoUseCase;

    /**
     * 경매 등록 API
     * 온보딩 완료한 사용자만 경매를 등록할 수 있다.
     *
     * @param request 경매 생성 요청
     * @return 생성된 경매 정보
     */
    @PostMapping
    @RequireOnboarding
    public ResponseEntity<ApiResponse<AuctionResponse>> createAuction(
            @Valid @RequestBody CreateAuctionRequest request
    ) {
        Long sellerId = SecurityUtils.getCurrentUserId();

        Auction auction = createAuctionUseCase.createAuction(request.toCommand(sellerId));
        AuctionResponse response = AuctionResponse.from(auction);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 경매 목록 조회 API
     *
     * @param status   경매 상태 필터 (선택)
     * @param category 카테고리 필터 (선택)
     * @param keyword  검색어 - 상품명 (선택)
     * @param pageable 페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuctionListResponse>>> getAuctionList(
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<Auction> auctions = getAuctionListUseCase.getAuctionList(status, category, keyword, pageable);
        Page<AuctionListResponse> response = auctions.map(AuctionListResponse::from);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 경매 상세 조회 API
     * 인증된 사용자가 있으면 해당 사용자의 낙찰 순위 정보도 포함
     * 진행 중인 경매에서는 현재 입찰 순위(1순위/2순위)도 포함
     *
     * @param auctionId 조회할 경매 ID
     * @return 경매 상세 정보
     */
    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionResponse>> getAuctionDetail(
            @PathVariable Long auctionId
    ) {
        Auction auction = getAuctionDetailUseCase.getAuctionDetail(auctionId);
        Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();

        // 낙찰 정보 (종료된 경매)
        Integer userWinningRank = null;
        String userWinningStatus = null;

        if (auction.getStatus() == AuctionStatus.ENDED) {
            UserWinningInfo winningInfo = getUserWinningInfoUseCase.getUserWinningInfo(auctionId, currentUserId);

            if (winningInfo != null) {
                userWinningRank = winningInfo.rank();
                userWinningStatus = winningInfo.status();
            }
        }

        // 입찰 순위 (진행 중인 경매) - Redis에서 가져온 topBidderId, secondBidderId와 비교
        Integer userBidRank = calculateUserBidRank(auction, currentUserId);

        AuctionResponse response = AuctionResponse.from(auction, userWinningRank, userWinningStatus, userBidRank);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 현재 사용자의 입찰 순위를 계산한다.
     * Redis에 저장된 topBidderId와 비교하여 1순위 여부 반환
     *
     * @param auction       경매 도메인 객체 (Redis에서 조회됨)
     * @param currentUserId 현재 사용자 ID
     * @return 1(1순위), null(1순위 아님 또는 비로그인)
     */
    private Integer calculateUserBidRank(Auction auction, Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }

        // 종료된 경매는 입찰 순위 표시 안 함 (낙찰 정보 사용)
        if (auction.isEnded()) {
            return null;
        }

        // 1순위 확인
        if (currentUserId.equals(auction.getTopBidderId())) {
            return 1;
        }

        return null;
    }
}
