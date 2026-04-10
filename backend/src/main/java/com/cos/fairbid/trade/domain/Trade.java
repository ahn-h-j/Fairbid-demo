package com.cos.fairbid.trade.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

import com.cos.fairbid.winning.domain.Winning;

/**
 * 거래 도메인 모델
 * 낙찰 후 판매자-구매자 간의 거래 연결을 관리한다.
 * 순수 비즈니스 로직만 포함 (JPA 의존성 없음)
 *
 * 응답 기한 관련 상수는 Winning 도메인에서 관리 (source of truth)
 */
@Getter
@Builder
public class Trade {

    /**
     * 택배 송장 입력 기한 (시간)
     */
    public static final int SHIPPING_DEADLINE_HOURS = 72;

    private Long id;
    private Long auctionId;
    private Long sellerId;
    private Long buyerId;
    private Long finalPrice;
    private TradeStatus status;
    private TradeMethod method;             // 선택된 거래 방식 (둘 다 가능 시 null)
    private LocalDateTime responseDeadline; // 응답 기한
    private LocalDateTime reminderSentAt;   // 리마인더 발송 시각 (중복 발송 방지)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    /**
     * 새로운 거래 생성 (낙찰 시 호출)
     *
     * @param auctionId             경매 ID
     * @param sellerId              판매자 ID
     * @param buyerId               구매자 ID (낙찰자)
     * @param finalPrice            낙찰가
     * @param directTradeAvailable  직거래 가능 여부
     * @param deliveryAvailable     택배 가능 여부
     * @return 생성된 Trade
     */
    public static Trade create(
            Long auctionId,
            Long sellerId,
            Long buyerId,
            Long finalPrice,
            boolean directTradeAvailable,
            boolean deliveryAvailable
    ) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(Winning.RESPONSE_DEADLINE_HOURS);

        // 거래 방식 및 초기 상태 결정
        TradeMethod method = null;
        TradeStatus status;

        if (directTradeAvailable && deliveryAvailable) {
            // 둘 다 가능 → 구매자가 선택해야 함
            status = TradeStatus.AWAITING_METHOD_SELECTION;
            method = null;
        } else if (directTradeAvailable) {
            // 직거래만 가능
            status = TradeStatus.AWAITING_ARRANGEMENT;
            method = TradeMethod.DIRECT;
        } else {
            // 택배만 가능
            status = TradeStatus.AWAITING_ARRANGEMENT;
            method = TradeMethod.DELIVERY;
        }

        return Trade.builder()
                .auctionId(auctionId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .finalPrice(finalPrice)
                .status(status)
                .method(method)
                .responseDeadline(deadline)
                .createdAt(now)
                .completedAt(null)
                .build();
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     */
    public static TradeBuilder reconstitute() {
        return Trade.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 거래 방식을 선택한다 (둘 다 가능한 경우 구매자가 호출)
     *
     * @param selectedMethod 선택된 거래 방식
     */
    public void selectMethod(TradeMethod selectedMethod) {
        if (this.status != TradeStatus.AWAITING_METHOD_SELECTION) {
            throw new IllegalStateException("거래 방식 선택 대기 상태에서만 선택 가능합니다. 현재 상태: " + this.status);
        }
        if (selectedMethod == null) {
            throw new IllegalArgumentException("거래 방식은 null일 수 없습니다.");
        }

        this.method = selectedMethod;
        this.status = TradeStatus.AWAITING_ARRANGEMENT;
        // 응답 기한 갱신
        this.responseDeadline = LocalDateTime.now().plusHours(Winning.RESPONSE_DEADLINE_HOURS);
    }

    /**
     * 거래 조율이 완료되었음을 표시한다
     * - 직거래: 약속 확정 시
     * - 택배: 발송 완료 시
     */
    public void markArranged() {
        if (this.status != TradeStatus.AWAITING_ARRANGEMENT) {
            throw new IllegalStateException("거래 조율 중 상태에서만 변경 가능합니다. 현재 상태: " + this.status);
        }
        this.status = TradeStatus.ARRANGED;
    }

    /**
     * 거래를 완료한다
     * ARRANGED 상태에서만 완료 가능 (조율 완료 후)
     */
    public void complete() {
        if (this.status != TradeStatus.ARRANGED) {
            throw new IllegalStateException(
                    "거래 완료 처리가 불가능한 상태입니다. "
                            + "조율이 완료된 상태(ARRANGED)에서만 완료 가능합니다. 현재 상태: "
                            + this.status);
        }
        this.status = TradeStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 거래를 취소한다 (노쇼/유찰)
     */
    public void cancel() {
        if (this.status == TradeStatus.COMPLETED || this.status == TradeStatus.CANCELLED) {
            throw new IllegalStateException("이미 완료되거나 취소된 거래입니다. 현재 상태: " + this.status);
        }
        this.status = TradeStatus.CANCELLED;
    }

    /**
     * 2순위에게 거래를 승계한다
     *
     * @param newBuyerId   새로운 구매자 ID (2순위)
     * @param newFinalPrice 새로운 낙찰가 (2순위 입찰가)
     */
    public void transferToSecondRank(Long newBuyerId, Long newFinalPrice) {
        // 이미 완료되거나 취소된 거래는 승계 불가
        if (this.status == TradeStatus.COMPLETED || this.status == TradeStatus.CANCELLED) {
            throw new IllegalStateException("이미 완료되거나 취소된 거래는 2순위 승계가 불가능합니다. 현재 상태: " + this.status);
        }
        if (newBuyerId == null) {
            throw new IllegalArgumentException("새로운 구매자 ID는 null일 수 없습니다.");
        }
        if (newFinalPrice == null || newFinalPrice <= 0) {
            throw new IllegalArgumentException("새로운 낙찰가는 0보다 커야 합니다.");
        }

        this.buyerId = newBuyerId;
        this.finalPrice = newFinalPrice;
        this.responseDeadline = LocalDateTime.now().plusHours(Winning.SECOND_RANK_DEADLINE_HOURS);

        // 상태 초기화 (거래 방식에 따라)
        if (this.method == null) {
            this.status = TradeStatus.AWAITING_METHOD_SELECTION;
        } else {
            this.status = TradeStatus.AWAITING_ARRANGEMENT;
        }
    }

    /**
     * 직거래인지 확인한다
     */
    public boolean isDirectTrade() {
        return this.method == TradeMethod.DIRECT;
    }

    /**
     * 택배인지 확인한다
     */
    public boolean isDelivery() {
        return this.method == TradeMethod.DELIVERY;
    }

    /**
     * 해당 사용자가 거래 참여자인지 확인한다
     */
    public boolean isParticipant(Long userId) {
        return this.sellerId.equals(userId) || this.buyerId.equals(userId);
    }

    /**
     * 해당 사용자가 판매자인지 확인한다
     */
    public boolean isSeller(Long userId) {
        return this.sellerId.equals(userId);
    }

    /**
     * 해당 사용자가 구매자인지 확인한다
     */
    public boolean isBuyer(Long userId) {
        return this.buyerId.equals(userId);
    }

    /**
     * 리마인더 발송됨 표시
     */
    public void markReminderSent() {
        this.reminderSentAt = LocalDateTime.now();
    }
}
