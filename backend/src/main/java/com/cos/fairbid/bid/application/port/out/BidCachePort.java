package com.cos.fairbid.bid.application.port.out;

/**
 * 입찰 캐시 아웃바운드 포트
 * Redis Lua 스크립트를 통한 원자적 입찰 처리
 */
public interface BidCachePort {

    /**
     * 캐시에 경매 정보가 있는지 확인한다
     *
     * @param auctionId 경매 ID
     * @return 캐시에 존재하면 true
     */
    boolean existsInCache(Long auctionId);

    /**
     * Lua 스크립트로 원자적 입찰 처리를 수행한다
     *
     * @param auctionId     경매 ID
     * @param bidAmount     입찰 금액 (ONE_TOUCH면 0, INSTANT_BUY면 0)
     * @param bidderId      입찰자 ID
     * @param bidType       입찰 유형 (ONE_TOUCH / DIRECT / INSTANT_BUY)
     * @param currentTimeMs 현재 시간 (밀리초, 경매 연장 판단용)
     * @return 입찰 결과 (성공 시 새 현재가, 실패 시 예외)
     */
    BidResult placeBidAtomic(Long auctionId, Long bidAmount, Long bidderId, String bidType, Long currentTimeMs);

    /**
     * 입찰 결과 DTO
     *
     * @param newCurrentPrice     갱신된 현재가
     * @param newTotalBidCount    갱신된 총 입찰 횟수
     * @param newBidIncrement     갱신된 입찰 단위
     * @param extended            경매 연장 여부
     * @param extensionCount      연장 횟수
     * @param scheduledEndTimeMs  종료 예정 시간 (밀리초)
     * @param instantBuyActivated 즉시 구매 활성화 여부
     */
    record BidResult(
            Long newCurrentPrice,
            Integer newTotalBidCount,
            Long newBidIncrement,
            Boolean extended,
            Integer extensionCount,
            Long scheduledEndTimeMs,
            Boolean instantBuyActivated
    ) { }
}
