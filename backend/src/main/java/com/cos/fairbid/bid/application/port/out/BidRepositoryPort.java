package com.cos.fairbid.bid.application.port.out;

import java.util.List;

import com.cos.fairbid.bid.domain.Bid;

/**
 * 입찰 저장소 아웃바운드 포트
 * 영속성 계층과의 통신을 위한 인터페이스
 */
public interface BidRepositoryPort {

    /**
     * 입찰 이력을 저장한다
     *
     * @param bid 저장할 입찰 도메인 객체
     * @return 저장된 입찰 (ID 포함)
     */
    Bid save(Bid bid);

    /**
     * 경매의 상위 2개 입찰을 조회한다 (금액 내림차순)
     * 낙찰자 결정 시 1, 2순위 추출에 사용
     *
     * @param auctionId 경매 ID
     * @return 상위 2개 입찰 목록 (금액 내림차순)
     */
    List<Bid> findTop2ByAuctionId(Long auctionId);

    /**
     * 입찰을 멱등하게 저장한다 (중복 시 무시)
     * Redis Stream Consumer의 at-least-once 전달 보장에서
     * 중복 처리를 방지하기 위해 streamRecordId 기반으로 중복 체크한다.
     *
     * @param bid 저장할 입찰 도메인 객체
     * @param streamRecordId Redis Stream 메시지 ID (중복 판별 키)
     * @return 저장 성공 시 true, 중복으로 스킵 시 false
     */
    boolean saveIdempotent(Bid bid, String streamRecordId);

    /**
     * 전체 입찰 건수를 조회한다
     * Redis-RDB 정합성 모니터링에 사용
     *
     * @return 전체 입찰 건수
     */
    long countAll();
}
