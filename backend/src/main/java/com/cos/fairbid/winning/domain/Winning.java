package com.cos.fairbid.winning.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 낙찰 도메인 모델
 * 1, 2순위 낙찰 후보 정보를 관리한다
 *
 * 결제 → 응답 기반 시스템으로 변경됨
 * - 낙찰자는 24시간 내에 거래 조율에 응답해야 함
 * - 2순위 승계 시 12시간 내에 응답해야 함
 */
@Getter
@Builder
public class Winning {

    private Long id;
    private Long auctionId;
    private Integer rank;           // 1 or 2
    private Long bidderId;
    private Long bidAmount;
    private WinningStatus status;
    private LocalDateTime responseDeadline;  // 응답 기한 (payment → response)
    private LocalDateTime createdAt;

    /** 응답 대기 시간 (24시간) - 거래 조율 응답 기한 */
    public static final int RESPONSE_DEADLINE_HOURS = 24;

    /** 2순위 승계 응답 대기 시간 (12시간) */
    public static final int SECOND_RANK_DEADLINE_HOURS = 12;

    /** 2순위 자동 승계 기준 비율 (90%) */
    public static final double AUTO_TRANSFER_THRESHOLD = 0.9;

    /**
     * 1순위 낙찰자 생성
     * 24시간 응답 기한이 설정됨
     *
     * @param auctionId 경매 ID
     * @param bidderId  입찰자 ID
     * @param bidAmount 입찰 금액
     * @return 1순위 Winning 객체
     * @throws IllegalArgumentException 필수 파라미터가 null인 경우
     */
    public static Winning createFirstRank(Long auctionId, Long bidderId, Long bidAmount) {
        if (auctionId == null) {
            throw new IllegalArgumentException("경매 ID는 null일 수 없습니다.");
        }
        if (bidderId == null) {
            throw new IllegalArgumentException("입찰자 ID는 null일 수 없습니다.");
        }
        if (bidAmount == null) {
            throw new IllegalArgumentException("입찰 금액은 null일 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        return Winning.builder()
                .auctionId(auctionId)
                .rank(1)
                .bidderId(bidderId)
                .bidAmount(bidAmount)
                .status(WinningStatus.PENDING_RESPONSE)
                .responseDeadline(now.plusHours(RESPONSE_DEADLINE_HOURS))
                .createdAt(now)
                .build();
    }

    /**
     * 2순위 낙찰 후보 생성
     * 초기 상태는 PENDING_RESPONSE지만 실제 응답 권한은 1순위 노쇼 시 부여됨
     *
     * @param auctionId 경매 ID
     * @param bidderId  입찰자 ID
     * @param bidAmount 입찰 금액
     * @return 2순위 Winning 객체
     * @throws IllegalArgumentException 필수 파라미터가 null인 경우
     */
    public static Winning createSecondRank(Long auctionId, Long bidderId, Long bidAmount) {
        if (auctionId == null) {
            throw new IllegalArgumentException("경매 ID는 null일 수 없습니다.");
        }
        if (bidderId == null) {
            throw new IllegalArgumentException("입찰자 ID는 null일 수 없습니다.");
        }
        if (bidAmount == null) {
            throw new IllegalArgumentException("입찰 금액은 null일 수 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        return Winning.builder()
                .auctionId(auctionId)
                .rank(2)
                .bidderId(bidderId)
                .bidAmount(bidAmount)
                .status(WinningStatus.STANDBY)  // 2순위는 대기 상태로 시작
                .responseDeadline(null)  // 승계 시 설정
                .createdAt(now)
                .build();
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     */
    public static WinningBuilder reconstitute() {
        return Winning.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 2순위가 자동 승계 대상인지 확인한다
     * 2순위 입찰가 >= 1순위 입찰가 * 90%
     *
     * @param firstRankAmount 1순위 입찰 금액
     * @return 자동 승계 대상이면 true
     */
    public boolean isEligibleForAutoTransfer(Long firstRankAmount) {
        if (this.rank != 2) {
            return false;
        }
        long threshold = (long) (firstRankAmount * AUTO_TRANSFER_THRESHOLD);
        return this.bidAmount >= threshold;
    }

    /**
     * 노쇼 처리한다
     * 상태를 NO_SHOW로 변경
     */
    public void markAsNoShow() {
        this.status = WinningStatus.NO_SHOW;
    }

    /**
     * 응답 완료 처리한다
     * 낙찰자가 거래 조율에 응답함
     */
    public void markAsResponded() {
        this.status = WinningStatus.RESPONDED;
    }

    /**
     * 유찰 처리한다
     */
    public void markAsFailed() {
        this.status = WinningStatus.FAILED;
    }

    /**
     * 2순위 승계 처리한다
     * STANDBY → PENDING_RESPONSE 상태로 전환하고 12시간 응답 대기 시간 부여
     *
     * @throws IllegalStateException 2순위가 아니거나 STANDBY 상태가 아닌 경우
     */
    public void transferToSecondRank() {
        if (this.rank != 2) {
            throw new IllegalStateException("2순위만 승계 가능합니다.");
        }
        if (this.status != WinningStatus.STANDBY) {
            throw new IllegalStateException("STANDBY 상태에서만 승계 가능합니다. 현재 상태: " + this.status);
        }
        this.status = WinningStatus.PENDING_RESPONSE;  // 대기 → 응답 대기로 전환
        this.responseDeadline = LocalDateTime.now().plusHours(SECOND_RANK_DEADLINE_HOURS);
    }

    /**
     * 응답 기한이 만료되었는지 확인한다
     *
     * @return 만료되었으면 true
     */
    public boolean isResponseExpired() {
        if (responseDeadline == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(responseDeadline);
    }

    /**
     * 응답 대기 중인지 확인한다
     *
     * @return 응답 대기 중이면 true
     */
    public boolean isPendingResponse() {
        return this.status == WinningStatus.PENDING_RESPONSE;
    }

    // =====================================================
    // 테스트 전용 메서드
    // =====================================================

    /**
     * [테스트 전용] 응답 기한을 강제로 만료시킨다.
     * 노쇼 처리 테스트를 위해 deadline을 과거로 설정한다.
     */
    public void expireResponseDeadlineForTest() {
        this.responseDeadline = LocalDateTime.now().minusHours(1);
    }
}
