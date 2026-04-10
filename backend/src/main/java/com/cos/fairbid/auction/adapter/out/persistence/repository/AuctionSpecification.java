package com.cos.fairbid.auction.adapter.out.persistence.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;

/**
 * 경매 목록 조회용 동적 쿼리 Specification
 */
public class AuctionSpecification {

    private AuctionSpecification() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * 검색 조건에 따른 Specification 생성
     *
     * @param status   경매 상태 필터 (nullable - null이면 진행 중인 경매만 조회)
     * @param category 카테고리 필터 (nullable)
     * @param keyword  검색어 - 상품명 (nullable)
     * @return Specification
     */
    public static Specification<AuctionEntity> withCondition(AuctionStatus status, Category category, String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // status 필터링
            if (status != null) {
                // 명시적으로 상태를 지정한 경우 해당 상태만 조회
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            } else {
                // status가 null이면 진행 중인 경매만 조회 (BIDDING, INSTANT_BUY_PENDING)
                // 종료된 경매(ENDED, FAILED, CANCELLED)는 목록에서 제외
                predicates.add(root.get("status").in(
                        AuctionStatus.BIDDING,
                        AuctionStatus.INSTANT_BUY_PENDING
                ));
            }

            // category 필터링
            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category));
            }

            // keyword 검색 (title LIKE %keyword%)
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        "%" + keyword.toLowerCase() + "%"
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
