package com.cos.fairbid.user.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.common.pagination.CursorPage;
import com.cos.fairbid.user.application.port.in.GetMyAuctionsUseCase;
import com.cos.fairbid.user.application.port.in.GetMyBidsUseCase;
import com.cos.fairbid.user.application.port.out.LoadUserAuctionsPort;
import com.cos.fairbid.user.application.port.out.LoadUserBidsPort;

/**
 * 마이페이지 서비스
 * 내 판매 경매 목록과 내 입찰 경매 목록을 커서 기반으로 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMyPageService implements GetMyAuctionsUseCase, GetMyBidsUseCase {

    private final LoadUserAuctionsPort loadUserAuctionsPort;
    private final LoadUserBidsPort loadUserBidsPort;

    /**
     * 내 판매 경매 목록을 조회한다.
     * size+1개를 조회하여 다음 페이지 존재 여부를 판단한다.
     */
    @Override
    public CursorPage<MyAuctionItem> getMyAuctions(Long userId, AuctionStatus status, Long cursor, int size) {
        // size+1개 조회하여 hasNext 판별
        List<MyAuctionItem> fetched = loadUserAuctionsPort.findBySellerIdWithCursor(userId, status, cursor, size + 1);

        boolean hasNext = fetched.size() > size;
        List<MyAuctionItem> items = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? items.get(items.size() - 1).id() : null;

        return new CursorPage<>(items, nextCursor, hasNext);
    }

    /**
     * 내 입찰 경매 목록을 조회한다.
     * size+1개를 조회하여 다음 페이지 존재 여부를 판단한다.
     */
    @Override
    public CursorPage<MyBidItem> getMyBids(Long userId, Long cursor, int size) {
        // size+1개 조회하여 hasNext 판별
        List<MyBidItem> fetched = loadUserBidsPort.findBidAuctionsByBidderWithCursor(userId, cursor, size + 1);

        boolean hasNext = fetched.size() > size;
        List<MyBidItem> items = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? items.get(items.size() - 1).auctionId() : null;

        return new CursorPage<>(items, nextCursor, hasNext);
    }
}
