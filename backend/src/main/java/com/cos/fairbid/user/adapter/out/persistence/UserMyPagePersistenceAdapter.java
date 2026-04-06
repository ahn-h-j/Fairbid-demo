package com.cos.fairbid.user.adapter.out.persistence;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.adapter.out.persistence.repository.JpaAuctionRepository;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.bid.adapter.out.persistence.repository.JpaBidRepository;
import com.cos.fairbid.user.application.port.in.GetMyAuctionsUseCase.MyAuctionItem;
import com.cos.fairbid.user.application.port.in.GetMyBidsUseCase.MyBidItem;
import com.cos.fairbid.user.application.port.out.LoadUserAuctionsPort;
import com.cos.fairbid.user.application.port.out.LoadUserBidsPort;
import com.cos.fairbid.winning.adapter.out.persistence.entity.WinningEntity;
import com.cos.fairbid.winning.adapter.out.persistence.repository.JpaWinningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 마이페이지 영속성 어댑터
 * 사용자의 판매 경매 목록과 입찰 경매 목록을 조회한다.
 * auction/bid 모듈의 JPA Repository를 사용하여 크로스모듈 조회를 수행한다.
 */
@Component
@RequiredArgsConstructor
public class UserMyPagePersistenceAdapter implements LoadUserAuctionsPort, LoadUserBidsPort {

    private final JpaAuctionRepository jpaAuctionRepository;
    private final JpaBidRepository jpaBidRepository;
    private final JpaWinningRepository jpaWinningRepository;

    /**
     * 판매자의 경매 목록을 커서 기반으로 조회한다.
     */
    @Override
    public List<MyAuctionItem> findBySellerIdWithCursor(Long sellerId, AuctionStatus status, Long cursor, int limit) {
        List<AuctionEntity> entities = jpaAuctionRepository.findBySellerIdWithCursor(
                sellerId, status, cursor, PageRequest.of(0, limit));

        return entities.stream()
                .map(e -> new MyAuctionItem(
                        e.getId(),
                        e.getTitle(),
                        e.getCurrentPrice(),
                        e.getStatus(),
                        e.getCreatedAt()))
                .toList();
    }

    /**
     * 입찰자의 입찰 경매 목록을 커서 기반으로 조회한다.
     * JPQL projection 결과(Object[])를 MyBidItem record로 변환한다.
     * Winning 테이블을 조회하여 낙찰 순위와 상태를 추가한다.
     */
    @Override
    public List<MyBidItem> findBidAuctionsByBidderWithCursor(Long bidderId, Long cursor, int limit) {
        List<Object[]> results = jpaBidRepository.findBidAuctionsByBidderWithCursor(
                bidderId, cursor, PageRequest.of(0, limit));

        // 입찰자의 낙찰 정보를 미리 조회하여 Map으로 변환 (auctionId -> WinningEntity)
        Map<Long, WinningEntity> winningMap = jpaWinningRepository.findByBidderId(bidderId).stream()
                .collect(Collectors.toMap(
                        WinningEntity::getAuctionId,
                        w -> w,
                        (existing, replacement) -> existing  // 중복 시 첫 번째 값 유지
                ));

        return results.stream()
                .map(row -> {
                    Long auctionId = (Long) row[0];
                    WinningEntity winning = winningMap.get(auctionId);
                    return new MyBidItem(
                            auctionId,                                      // auctionId
                            (String) row[1],                                // title
                            (Long) row[2],                                  // myHighestBid (MAX(b.amount))
                            (Long) row[3],                                  // currentPrice
                            (AuctionStatus) row[4],                         // status
                            (LocalDateTime) row[5],                         // createdAt
                            winning != null ? winning.getRank() : null,     // winnerRank
                            winning != null ? winning.getStatus().name() : null  // winningStatus
                    );
                })
                .toList();
    }
}
