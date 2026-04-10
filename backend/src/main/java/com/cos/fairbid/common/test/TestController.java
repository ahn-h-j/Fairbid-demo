package com.cos.fairbid.common.test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.trade.application.port.out.TradeRepositoryPort;
import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;
import com.cos.fairbid.winning.application.port.in.ProcessNoShowUseCase;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;

/**
 * 테스트용 컨트롤러
 * 개발/테스트 환경에서만 사용 가능
 *
 * 주의: 프로덕션 환경에서는 비활성화되어야 함
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
public class TestController {

    private static final String AUCTION_KEY_PREFIX = "auction:";

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final CloseAuctionUseCase closeAuctionUseCase;
    private final StringRedisTemplate redisTemplate;
    private final WinningRepositoryPort winningRepositoryPort;
    private final TradeRepositoryPort tradeRepositoryPort;
    private final ProcessNoShowUseCase processNoShowUseCase;
    private final TestNoShowHelper testNoShowHelper;

    /**
     * 경매 종료 시간을 현재로부터 5분 후로 설정 (연장 테스트용)
     *
     * @param auctionId 경매 ID
     * @return 변경된 경매 정보
     */
    @PostMapping("/auctions/{auctionId}/set-ending-soon")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setEndingSoon(
            @PathVariable Long auctionId
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. 종료 시간을 현재 + 5분으로 변경 (Redis Hash + Sorted Set 업데이트)
        String key = AUCTION_KEY_PREFIX + auctionId;
        LocalDateTime newEndTime = LocalDateTime.now().plusMinutes(5);
        long newEndTimeMs = newEndTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForHash().put(key, "scheduledEndTime", newEndTime.toString());
        redisTemplate.opsForHash().put(key, "scheduledEndTimeMs", String.valueOf(newEndTimeMs));
        auctionCachePort.addToClosingQueue(auctionId, newEndTimeMs);

        // 3. RDB도 함께 업데이트 (목록 조회 시 정확한 시간 표시를 위해)
        auction.updateScheduledEndTime(newEndTime);
        auctionRepository.save(auction);

        log.info("[TEST] 경매 종료 시간 변경: auctionId={}, newEndTime={}", auctionId, newEndTime);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "newScheduledEndTime", newEndTime.toString(),
                "message", "경매 종료 시간이 5분 후로 변경되었습니다."
        )));
    }

    /**
     * 경매 종료 시간을 현재로부터 지정한 초 후로 설정
     *
     * @param auctionId 경매 ID
     * @param seconds   초 단위 시간
     * @return 변경된 경매 정보
     */
    @PostMapping("/auctions/{auctionId}/set-end-time")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setEndTime(
            @PathVariable Long auctionId,
            @RequestParam(defaultValue = "300") int seconds
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. 종료 시간 변경 (Redis Hash + Sorted Set 업데이트)
        String key = AUCTION_KEY_PREFIX + auctionId;
        LocalDateTime newEndTime = LocalDateTime.now().plusSeconds(seconds);
        long newEndTimeMs = newEndTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForHash().put(key, "scheduledEndTime", newEndTime.toString());
        redisTemplate.opsForHash().put(key, "scheduledEndTimeMs", String.valueOf(newEndTimeMs));
        auctionCachePort.addToClosingQueue(auctionId, newEndTimeMs);

        // 3. RDB도 함께 업데이트 (목록 조회 시 정확한 시간 표시를 위해)
        auction.updateScheduledEndTime(newEndTime);
        auctionRepository.save(auction);

        log.info("[TEST] 경매 종료 시간 변경: auctionId={}, newEndTime={}, seconds={}", auctionId, newEndTime, seconds);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "newScheduledEndTime", newEndTime.toString(),
                "seconds", seconds,
                "message", String.format("경매 종료 시간이 %d초 후로 변경되었습니다.", seconds)
        )));
    }

    /**
     * 경매 강제 종료 (종료 처리 스케줄러 즉시 실행)
     *
     * @param auctionId 경매 ID
     * @return 처리 결과
     */
    @PostMapping("/auctions/{auctionId}/force-close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forceClose(
            @PathVariable Long auctionId
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. 종료 시간을 과거로 설정 (Redis Hash + Sorted Set 업데이트)
        String key = AUCTION_KEY_PREFIX + auctionId;
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        long pastTimeMs = pastTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForHash().put(key, "scheduledEndTime", pastTime.toString());
        redisTemplate.opsForHash().put(key, "scheduledEndTimeMs", String.valueOf(pastTimeMs));
        auctionCachePort.addToClosingQueue(auctionId, pastTimeMs);

        // 3. RDB도 함께 업데이트
        auction.updateScheduledEndTime(pastTime);
        auctionRepository.save(auction);

        // 4. 스케줄러 즉시 실행
        closeAuctionUseCase.closeExpiredAuctions();

        log.info("[TEST] 경매 강제 종료: auctionId={}", auctionId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "message", "경매가 강제 종료되었습니다."
        )));
    }

    /**
     * Redis 캐시 새로고침 (RDB -> Redis)
     *
     * @param auctionId 경매 ID
     * @return 처리 결과
     */
    @PostMapping("/auctions/{auctionId}/refresh-cache")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshCache(
            @PathVariable Long auctionId
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. Redis 캐시 갱신
        auctionCachePort.saveToCache(auction);

        log.info("[TEST] 캐시 새로고침: auctionId={}", auctionId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "message", "캐시가 새로고침되었습니다."
        )));
    }

    // =====================================================
    // 노쇼 테스트 관련 API
    // =====================================================

    /**
     * 특정 경매의 응답 기한을 강제로 만료시키고 노쇼 처리를 실행한다.
     *
     * 처리 순서:
     * 1. 해당 경매의 1순위 Winning의 responseDeadline을 과거로 변경 (별도 트랜잭션)
     * 2. 노쇼 처리 스케줄러 로직 즉시 실행
     *
     * @param auctionId 경매 ID
     * @return 처리 결과 (노쇼 처리 전/후 상태)
     */
    @PostMapping("/auctions/{auctionId}/force-noshow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forceNoShow(
            @PathVariable Long auctionId
    ) {
        log.info("[TEST] 노쇼 강제 처리 시작 - auctionId: {}", auctionId);

        Map<String, Object> result = new HashMap<>();
        result.put("auctionId", auctionId);

        // 1. deadline 만료 처리 (별도 트랜잭션으로 즉시 커밋)
        Map<String, Object> expireResult = testNoShowHelper.expireDeadlineForTest(auctionId);
        result.putAll(expireResult);

        log.info("[TEST] deadline 변경 완료, 노쇼 처리 실행");

        // 2. 노쇼 처리 즉시 실행 (변경사항이 커밋된 후 실행)
        processNoShowUseCase.processExpiredPayments();

        // 3. 처리 후 상태 조회
        winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1).ifPresent(winning -> {
            result.put("afterFirstWinningStatus", winning.getStatus().name());
        });

        winningRepositoryPort.findByAuctionIdAndRank(auctionId, 2).ifPresent(secondWinning -> {
            result.put("afterSecondWinningStatus", secondWinning.getStatus().name());
            if (secondWinning.getResponseDeadline() != null) {
                result.put("secondWinningNewDeadline", secondWinning.getResponseDeadline().toString());
            }
        });

        tradeRepositoryPort.findByAuctionId(auctionId).ifPresent(trade -> {
            result.put("afterTradeStatus", trade.getStatus().name());
            result.put("afterTradeBuyerId", trade.getBuyerId());
        });

        log.info("[TEST] 노쇼 강제 처리 완료 - result: {}", result);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 현재 경매의 낙찰/거래 상태를 조회한다.
     *
     * @param auctionId 경매 ID
     * @return 낙찰 및 거래 상태 정보
     */
    @GetMapping("/auctions/{auctionId}/winning-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWinningStatus(
            @PathVariable Long auctionId
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("auctionId", auctionId);

        // 1순위 정보
        winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1).ifPresent(winning -> {
            result.put("firstWinning", Map.of(
                    "id", winning.getId(),
                    "bidderId", winning.getBidderId(),
                    "bidAmount", winning.getBidAmount(),
                    "status", winning.getStatus().name(),
                    "responseDeadline", winning.getResponseDeadline() != null
                            ? winning.getResponseDeadline().toString() : "null"
            ));
        });

        // 2순위 정보
        winningRepositoryPort.findByAuctionIdAndRank(auctionId, 2).ifPresent(winning -> {
            result.put("secondWinning", Map.of(
                    "id", winning.getId(),
                    "bidderId", winning.getBidderId(),
                    "bidAmount", winning.getBidAmount(),
                    "status", winning.getStatus().name(),
                    "responseDeadline", winning.getResponseDeadline() != null
                            ? winning.getResponseDeadline().toString() : "null"
            ));
        });

        // Trade 정보
        tradeRepositoryPort.findByAuctionId(auctionId).ifPresent(trade -> {
            result.put("trade", Map.of(
                    "id", trade.getId(),
                    "buyerId", trade.getBuyerId(),
                    "finalPrice", trade.getFinalPrice(),
                    "status", trade.getStatus().name(),
                    "method", trade.getMethod() != null ? trade.getMethod().name() : "null",
                    "responseDeadline", trade.getResponseDeadline() != null
                            ? trade.getResponseDeadline().toString() : "null"
            ));
        });

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
