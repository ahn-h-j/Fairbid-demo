package com.cos.fairbid.bid.adapter.out.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.bid.application.port.out.BidCachePort.BidResult;
import com.cos.fairbid.bid.application.port.out.BidEventPublisherPort;
import com.cos.fairbid.bid.domain.event.BidPlacedEvent;

/**
 * 입찰 이벤트 발행 어댑터
 * BidEventPublisherPort 포트 구현체
 */
@Component
@RequiredArgsConstructor
public class BidEventPublisherAdapter implements BidEventPublisherPort {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 입찰 완료 이벤트를 발행한다
     * 실시간 UI 업데이트용 (현재가, 종료시간, 다음 입찰가, 입찰 단위, 총 입찰수, 현재 1순위 입찰자)
     *
     * @param auctionId   경매 ID
     * @param result      Lua 스크립트 입찰 결과 (최신 값)
     * @param topBidderId 현재 1순위 입찰자 ID (입찰 성공한 사람)
     */
    @Override
    public void publishBidPlaced(Long auctionId, BidResult result, Long topBidderId) {
        // 밀리초 → LocalDateTime 변환
        LocalDateTime scheduledEndTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(result.scheduledEndTimeMs()),
                ZoneId.systemDefault()
        );

        // 다음 최소 입찰가 계산
        Long nextMinBidPrice = result.newCurrentPrice() + result.newBidIncrement();

        BidPlacedEvent event = BidPlacedEvent.of(
                auctionId,
                result.newCurrentPrice(),
                scheduledEndTime,
                result.extended(),
                nextMinBidPrice,
                result.newBidIncrement(),
                result.newTotalBidCount(),
                topBidderId
        );
        eventPublisher.publishEvent(event);
    }
}
