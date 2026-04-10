package com.cos.fairbid.bid.adapter.in.stream;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;

/**
 * Redis Stream 메시지 처리 핸들러 (별도 빈)
 *
 * BidStreamConsumer에서 분리된 이유:
 * StreamMessageListenerContainer가 this::onMessage로 콜백을 등록하면
 * Spring 프록시를 우회하여 @Transactional이 적용되지 않는다.
 * 별도 빈으로 분리하여 프록시 기반 트랜잭션이 정상 동작하도록 한다.
 */
@Component
@RequiredArgsConstructor
@EnabledOnRole({"api", "all"})
@Slf4j
public class BidStreamMessageHandler {

    private final BidRepositoryPort bidRepository;
    private final AuctionRepositoryPort auctionRepository;

    /**
     * 메시지 타입에 따라 분기하여 RDB에 저장한다.
     * 트랜잭션 커밋 후 호출자(BidStreamConsumer)가 ACK를 전송한다.
     *
     * @param body     메시지 필드 맵
     * @param recordId Stream Record ID (멱등 저장에 사용)
     * @throws IllegalArgumentException 알 수 없는 메시지 타입
     */
    @Transactional
    public void handle(Map<String, String> body, String recordId) {
        String type = body.get("type");

        if (type == null) {
            log.warn("메시지 타입 누락, 스킵: recordId={}", recordId);
            return;
        }

        switch (type) {
            case "BID_SAVE" -> processBidSave(body, recordId);
            case "INSTANT_BUY_UPDATE" -> processInstantBuyUpdate(body);
            default -> log.warn("알 수 없는 메시지 타입: type={}, recordId={}", type, recordId);
        }
    }

    /**
     * BID_SAVE 메시지 처리: 입찰 이력을 RDB에 멱등하게 저장한다.
     * streamRecordId unique 제약으로 at-least-once 중복 처리를 방지한다.
     */
    private void processBidSave(Map<String, String> body, String recordId) {
        Bid bid = Bid.reconstitute()
                .auctionId(Long.parseLong(body.get("auctionId")))
                .bidderId(Long.parseLong(body.get("bidderId")))
                .amount(Long.parseLong(body.get("amount")))
                .bidType(BidType.valueOf(body.get("bidType")))
                .createdAt(LocalDateTime.parse(body.get("createdAt")))
                .build();
        bidRepository.saveIdempotent(bid, recordId);
    }

    /**
     * INSTANT_BUY_UPDATE 메시지 처리: 경매 즉시 구매 상태를 RDB에 업데이트한다.
     */
    private void processInstantBuyUpdate(Map<String, String> body) {
        auctionRepository.updateInstantBuyActivated(
                Long.parseLong(body.get("auctionId")),
                Long.parseLong(body.get("currentPrice")),
                Integer.parseInt(body.get("totalBidCount")),
                Long.parseLong(body.get("bidIncrement")),
                Long.parseLong(body.get("bidderId")),
                Long.parseLong(body.get("currentTimeMs")),
                Long.parseLong(body.get("scheduledEndTimeMs"))
        );
    }
}
