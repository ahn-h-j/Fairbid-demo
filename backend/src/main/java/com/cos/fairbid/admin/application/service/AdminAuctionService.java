package com.cos.fairbid.admin.application.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.admin.application.dto.AdminAuctionResult;
import com.cos.fairbid.admin.application.port.in.ManageAuctionUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionListUseCase;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.domain.User;

/**
 * 관리자 경매 관리 서비스
 * 경매 목록 조회 시 판매자 정보를 함께 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuctionService implements ManageAuctionUseCase {

    private final GetAuctionListUseCase getAuctionListUseCase;
    private final LoadUserPort loadUserPort;

    @Override
    public Page<AdminAuctionResult> getAuctionList(AuctionStatus status, String keyword, Pageable pageable) {
        // 1. 경매 목록 조회 (관리자 페이지에서는 카테고리 필터 없음)
        Page<Auction> auctions = getAuctionListUseCase.getAuctionList(status, null, keyword, pageable);

        // 2. 판매자 ID 수집
        Set<Long> sellerIds = auctions.getContent().stream()
                .map(Auction::getSellerId)
                .collect(Collectors.toSet());

        // 3. 판매자 정보 일괄 조회 (N+1 방지)
        Map<Long, String> sellerNicknameMap = loadUserPort.findAllByIds(sellerIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        User::getNickname
                ));

        // 4. AdminAuctionResult로 변환 (탈퇴한 사용자 처리)
        return auctions.map(auction ->
                AdminAuctionResult.from(
                        auction,
                        sellerNicknameMap.getOrDefault(auction.getSellerId(), "탈퇴한 사용자")
                )
        );
    }
}
