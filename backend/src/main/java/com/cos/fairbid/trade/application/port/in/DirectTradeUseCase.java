package com.cos.fairbid.trade.application.port.in;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import com.cos.fairbid.trade.domain.DirectTradeInfo;

/**
 * 직거래 유스케이스
 * 직거래 시간 조율 관련 인바운드 포트
 */
public interface DirectTradeUseCase {

    /**
     * 직거래 정보를 조회한다
     *
     * @param tradeId 거래 ID
     * @return 직거래 정보 (Optional)
     */
    Optional<DirectTradeInfo> findByTradeId(Long tradeId);

    /**
     * 직거래 시간을 제안한다 (판매자가 첫 제안)
     *
     * @param tradeId     거래 ID
     * @param userId      요청자 ID (판매자)
     * @param meetingDate 만남 날짜
     * @param meetingTime 만남 시간
     * @return 생성된 직거래 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 판매자가 아닌 경우
     * @throws com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException 직거래가 아닌 경우
     */
    DirectTradeInfo propose(Long tradeId, Long userId, LocalDate meetingDate, LocalTime meetingTime);

    /**
     * 제안을 수락한다 (약속 확정)
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (제안을 받은 사람)
     * @return 수락된 직거래 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 거래 참여자가 아닌 경우
     */
    DirectTradeInfo accept(Long tradeId, Long userId);

    /**
     * 역제안을 한다
     *
     * @param tradeId     거래 ID
     * @param userId      요청자 ID (역제안자)
     * @param meetingDate 새로운 날짜
     * @param meetingTime 새로운 시간
     * @return 업데이트된 직거래 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 거래 참여자가 아닌 경우
     */
    DirectTradeInfo counterPropose(Long tradeId, Long userId, LocalDate meetingDate, LocalTime meetingTime);
}
