package com.cos.fairbid.trade.domain;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Getter;

/**
 * 직거래 정보 도메인 모델
 * 직거래 시간 조율 정보를 관리한다.
 * 위치는 Auction에 저장되어 있으므로 여기서는 시간만 관리한다.
 */
@Getter
@Builder
public class DirectTradeInfo {

    private Long id;
    private Long tradeId;
    private String location;            // 거래 장소 (Auction에서 복사)
    private LocalDate meetingDate;      // 만남 날짜
    private LocalTime meetingTime;      // 만남 시간
    private DirectTradeStatus status;   // 조율 상태
    private Long proposedBy;            // 제안자 ID

    /**
     * 새로운 직거래 정보 생성 (판매자 첫 제안 시)
     *
     * @param tradeId      거래 ID
     * @param location     거래 장소 (Auction에서 복사)
     * @param meetingDate  만남 날짜
     * @param meetingTime  만남 시간
     * @param proposedBy   제안자 ID (판매자)
     * @return 생성된 DirectTradeInfo
     */
    public static DirectTradeInfo create(
            Long tradeId,
            String location,
            LocalDate meetingDate,
            LocalTime meetingTime,
            Long proposedBy
    ) {
        validateProposal(meetingDate, meetingTime);

        return DirectTradeInfo.builder()
                .tradeId(tradeId)
                .location(location)
                .meetingDate(meetingDate)
                .meetingTime(meetingTime)
                .status(DirectTradeStatus.PROPOSED)
                .proposedBy(proposedBy)
                .build();
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     */
    public static DirectTradeInfoBuilder reconstitute() {
        return DirectTradeInfo.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 제안을 수락한다 (약속 확정)
     */
    public void accept() {
        if (this.status == DirectTradeStatus.ACCEPTED) {
            throw new IllegalStateException("이미 수락된 약속입니다.");
        }
        this.status = DirectTradeStatus.ACCEPTED;
    }

    /**
     * 역제안을 한다
     *
     * @param newDate      새로운 날짜
     * @param newTime      새로운 시간
     * @param proposedBy   역제안자 ID
     */
    public void counterPropose(LocalDate newDate, LocalTime newTime, Long proposedBy) {
        if (this.status == DirectTradeStatus.ACCEPTED) {
            throw new IllegalStateException("이미 수락된 약속은 역제안할 수 없습니다.");
        }
        validateProposal(newDate, newTime);

        this.meetingDate = newDate;
        this.meetingTime = newTime;
        this.status = DirectTradeStatus.COUNTER_PROPOSED;
        this.proposedBy = proposedBy;
    }

    /**
     * 약속이 확정되었는지 확인한다
     */
    public boolean isAccepted() {
        return this.status == DirectTradeStatus.ACCEPTED;
    }

    /**
     * 제안/역제안의 유효성을 검증한다
     * - 날짜/시간 필수
     * - 과거 날짜 불가
     * - 당일인 경우 과거 시간 불가
     */
    private static void validateProposal(LocalDate date, LocalTime time) {
        if (date == null) {
            throw new IllegalArgumentException("만남 날짜는 필수입니다.");
        }
        if (time == null) {
            throw new IllegalArgumentException("만남 시간은 필수입니다.");
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new IllegalArgumentException("만남 날짜는 오늘 이후여야 합니다.");
        }
        // 당일인 경우 과거 시간 체크
        if (date.isEqual(today) && time.isBefore(LocalTime.now())) {
            throw new IllegalArgumentException("당일 약속은 현재 시간 이후로만 설정 가능합니다.");
        }
    }
}
