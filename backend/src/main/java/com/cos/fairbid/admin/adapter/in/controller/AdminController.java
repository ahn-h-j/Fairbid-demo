package com.cos.fairbid.admin.adapter.in.controller;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.admin.application.dto.AdminAuctionResult;
import com.cos.fairbid.admin.application.dto.AdminUserResult;
import com.cos.fairbid.admin.application.dto.DailyAuctionStatsResult;
import com.cos.fairbid.admin.application.dto.StatsOverviewResult;
import com.cos.fairbid.admin.application.dto.TimePatternResult;
import com.cos.fairbid.admin.application.port.in.GetStatsUseCase;
import com.cos.fairbid.admin.application.port.in.ManageAuctionUseCase;
import com.cos.fairbid.admin.application.port.in.ManageUserUseCase;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 컨트롤러
 * 관리자 전용 API를 제공한다.
 *
 * - 통계 조회
 * - 경매 관리
 * - 유저 관리
 *
 * 모든 엔드포인트는 ADMIN 역할 필요
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@EnabledOnRole({"api", "all"})
public class AdminController {

    /**
     * 허용되는 days 파라미터 값
     */
    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30);

    private final GetStatsUseCase getStatsUseCase;
    private final ManageAuctionUseCase manageAuctionUseCase;
    private final ManageUserUseCase manageUserUseCase;

    /**
     * 관리자 권한 확인용 테스트 엔드포인트
     * ADMIN 역할이 있어야 접근 가능하다.
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        log.info("관리자 헬스체크 호출");
        return ResponseEntity.ok(ApiResponse.success("Admin API is working"));
    }

    // ========== 통계 API ==========

    /**
     * 통계 개요를 조회한다.
     * 낙찰률, 평균 경쟁률, 평균 상승률, 연장 발생률을 반환한다.
     *
     * @param days 조회 기간 (일) - 7, 30 또는 null(전체)
     */
    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<StatsOverviewResult>> getStatsOverview(
            @RequestParam(required = false) Integer days) {
        Integer validDays = validateDays(days);
        log.debug("통계 개요 조회: days={}", validDays);
        StatsOverviewResult response = getStatsUseCase.getOverview(validDays);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 일별 경매 통계를 조회한다.
     * 일별 신규 경매, 낙찰 완료, 입찰 수를 반환한다.
     *
     * @param days 조회 기간 (일) - 7, 30 또는 null(전체)
     */
    @GetMapping("/stats/auctions")
    public ResponseEntity<ApiResponse<DailyAuctionStatsResult>> getDailyAuctionStats(
            @RequestParam(required = false) Integer days) {
        Integer validDays = validateDays(days);
        log.debug("일별 경매 통계 조회: days={}", validDays);
        DailyAuctionStatsResult response = getStatsUseCase.getDailyStats(validDays);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 시간대별 입찰 패턴을 조회한다.
     * 0~23시 각 시간대별 입찰 수와 피크 시간대를 반환한다.
     *
     * @param days 조회 기간 (일) - 7, 30 또는 null(전체)
     */
    @GetMapping("/stats/time-pattern")
    public ResponseEntity<ApiResponse<TimePatternResult>> getTimePattern(
            @RequestParam(required = false) Integer days) {
        Integer validDays = validateDays(days);
        log.debug("시간대별 입찰 패턴 조회: days={}", validDays);
        TimePatternResult response = getStatsUseCase.getTimePattern(validDays);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== 경매 관리 API ==========

    /**
     * 관리자용 경매 목록을 조회한다.
     * 판매자 정보를 포함한다.
     *
     * @param status   경매 상태 필터 (선택)
     * @param keyword  검색어 - 상품명 (선택)
     * @param pageable 페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    @GetMapping("/auctions")
    public ResponseEntity<ApiResponse<Page<AdminAuctionResult>>> getAuctionList(
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.debug("관리자 경매 목록 조회: status={}, keyword={}", status, keyword);
        Page<AdminAuctionResult> response = manageAuctionUseCase.getAuctionList(status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== 유저 관리 API ==========

    /**
     * 유저 목록을 조회한다.
     *
     * @param keyword  검색어 - 닉네임 또는 이메일 (선택)
     * @param pageable 페이지네이션 정보
     * @return 유저 목록 (페이지)
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResult>>> getUserList(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.debug("관리자 유저 목록 조회: keyword={}", keyword);
        Page<AdminUserResult> response = manageUserUseCase.getUserList(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * days 파라미터를 검증한다.
     * 허용된 값: 7, 30, null
     * 허용되지 않은 값은 null(전체 조회)로 처리하고 경고 로그를 남긴다.
     */
    private Integer validateDays(Integer days) {
        if (days == null) {
            return null;
        }
        if (!ALLOWED_DAYS.contains(days)) {
            log.warn("유효하지 않은 days 파라미터: {}. 전체 조회로 처리합니다. 허용값: {}", days, ALLOWED_DAYS);
            return null;
        }
        return days;
    }
}
