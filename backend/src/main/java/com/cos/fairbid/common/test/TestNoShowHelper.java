package com.cos.fairbid.common.test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;

/**
 * 테스트용 노쇼 처리 헬퍼
 * 트랜잭션 분리를 위해 별도 컴포넌트로 구현
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestNoShowHelper {

    private final WinningRepositoryPort winningRepositoryPort;
    private final TradeRepositoryPort tradeRepositoryPort;

    /**
     * deadline을 만료시키는 별도 트랜잭션
     * 이 메서드가 완료되면 변경사항이 즉시 DB에 커밋된다.
     *
     * @param auctionId 경매 ID
     * @return 처리 결과 정보
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> expireDeadlineForTest(Long auctionId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 1순위 Winning 조회 및 deadline 변경
        Winning firstWinning = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1)
                .orElseThrow(() -> new IllegalArgumentException("1순위 낙찰자가 없습니다. auctionId: " + auctionId));

        String beforeWinningStatus = firstWinning.getStatus().name();
        LocalDateTime beforeWinningDeadline = firstWinning.getResponseDeadline();

        firstWinning.expireResponseDeadlineForTest();
        winningRepositoryPort.save(firstWinning);

        result.put("firstWinning", Map.of(
                "id", firstWinning.getId(),
                "bidderId", firstWinning.getBidderId(),
                "beforeStatus", beforeWinningStatus,
                "beforeDeadline", beforeWinningDeadline != null ? beforeWinningDeadline.toString() : "null",
                "afterDeadline", firstWinning.getResponseDeadline().toString()
        ));

        // 2. 2순위 정보 확인
        Long firstBidAmount = firstWinning.getBidAmount();
        winningRepositoryPort.findByAuctionIdAndRank(auctionId, 2).ifPresent(secondWinning -> {
            boolean isEligible = secondWinning.isEligibleForAutoTransfer(firstBidAmount);
            result.put("secondWinning", Map.of(
                    "id", secondWinning.getId(),
                    "bidderId", secondWinning.getBidderId(),
                    "bidAmount", secondWinning.getBidAmount(),
                    "isEligibleForTransfer", isEligible,
                    "threshold", (long) (firstBidAmount * 0.9)
            ));
        });

        log.info("[TEST] deadline 만료 처리 완료 - auctionId: {}", auctionId);

        return result;
    }
}
