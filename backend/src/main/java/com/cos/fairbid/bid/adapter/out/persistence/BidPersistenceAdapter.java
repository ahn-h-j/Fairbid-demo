package com.cos.fairbid.bid.adapter.out.persistence;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.bid.adapter.out.persistence.entity.BidEntity;
import com.cos.fairbid.bid.adapter.out.persistence.mapper.BidMapper;
import com.cos.fairbid.bid.adapter.out.persistence.repository.JpaBidRepository;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.bid.domain.Bid;

/**
 * 입찰 영속성 어댑터
 * BidRepositoryPort 포트 구현체
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class BidPersistenceAdapter implements BidRepositoryPort {

    private final JpaBidRepository jpaBidRepository;
    private final BidMapper bidMapper;

    @Override
    public Bid save(Bid bid) {
        BidEntity entity = bidMapper.toEntity(bid);
        BidEntity savedEntity = jpaBidRepository.save(entity);
        return bidMapper.toDomain(savedEntity);
    }

    /**
     * 멱등 저장: streamRecordId unique 제약으로 중복 INSERT 방지
     * 이미 처리된 메시지면 DataIntegrityViolationException → false 반환
     */
    @Override
    public boolean saveIdempotent(Bid bid, String streamRecordId) {
        try {
            BidEntity entity = BidEntity.builder()
                    .auctionId(bid.getAuctionId())
                    .bidderId(bid.getBidderId())
                    .amount(bid.getAmount())
                    .bidType(bid.getBidType())
                    .streamRecordId(streamRecordId)
                    .build();
            jpaBidRepository.save(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            // streamRecordId unique 제약 위반만 중복으로 판단
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("stream_record_id")) {
                log.debug("멱등 저장 스킵 (중복 메시지): streamRecordId={}", streamRecordId);
                return false;
            }
            // 그 외 제약 위반은 재throw (NOT NULL, FK 등 실제 데이터 오류)
            throw e;
        }
    }

    @Override
    public List<Bid> findTop2ByAuctionId(Long auctionId) {
        return jpaBidRepository.findTop2ByAuctionIdOrderByAmountDesc(auctionId)
                .stream()
                .map(bidMapper::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return jpaBidRepository.count();
    }
}
