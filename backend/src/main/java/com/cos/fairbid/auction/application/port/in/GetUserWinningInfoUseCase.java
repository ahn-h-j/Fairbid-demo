package com.cos.fairbid.auction.application.port.in;

/**
 * 사용자의 낙찰 정보 조회 UseCase
 * 경매 상세 조회 시 현재 사용자의 낙찰 순위/상태 정보를 제공한다.
 */
public interface GetUserWinningInfoUseCase {

    /**
     * 특정 경매에서 사용자의 낙찰 정보를 조회한다.
     *
     * @param auctionId 경매 ID
     * @param userId    사용자 ID
     * @return 사용자의 낙찰 정보 (낙찰자가 아니면 null)
     */
    UserWinningInfo getUserWinningInfo(Long auctionId, Long userId);

    /**
     * 사용자 낙찰 정보 DTO
     */
    record UserWinningInfo(
            Integer rank,   // 낙찰 순위 (1 또는 2)
            String status   // 낙찰 상태 (PENDING_RESPONSE, RESPONDED, NO_SHOW, FAILED, STANDBY)
    ) { }
}
