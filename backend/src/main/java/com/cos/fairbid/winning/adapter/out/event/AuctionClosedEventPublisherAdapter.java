package com.cos.fairbid.winning.adapter.out.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.cos.fairbid.winning.application.event.AuctionClosedEvent;
import com.cos.fairbid.winning.application.port.out.AuctionClosedEventPublisherPort;

/**
 * 경매 종료 이벤트 발행 어댑터
 * AuctionClosedEventPublisherPort 포트 구현체
 */
@Component
@RequiredArgsConstructor
public class AuctionClosedEventPublisherAdapter implements AuctionClosedEventPublisherPort {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 경매 종료 이벤트를 발행한다
     * 트랜잭션 커밋 후 AuctionClosedEventListener에서 브로드캐스트 실행
     *
     * @param auctionId 종료된 경매 ID
     */
    @Override
    public void publishAuctionClosed(Long auctionId) {
        eventPublisher.publishEvent(new AuctionClosedEvent(auctionId));
    }
}
