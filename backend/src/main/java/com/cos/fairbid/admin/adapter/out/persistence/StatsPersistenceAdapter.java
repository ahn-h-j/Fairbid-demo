package com.cos.fairbid.admin.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.admin.application.port.out.LoadStatsPort;
import com.cos.fairbid.auction.domain.AuctionStatus;

/**
 * 통계 데이터 조회 어댑터
 * 네이티브 쿼리로 통계 데이터를 집계한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatsPersistenceAdapter implements LoadStatsPort {

    @PersistenceContext
    private final EntityManager em;

    @Override
    public long countTotalAuctions(LocalDateTime from) {
        String jpql = "SELECT COUNT(a) FROM AuctionEntity a";
        if (from != null) {
            jpql += " WHERE a.createdAt >= :from";
        }

        var query = em.createQuery(jpql, Long.class);
        if (from != null) {
            query.setParameter("from", from);
        }
        return query.getSingleResult();
    }

    @Override
    public long countCompletedAuctions(LocalDateTime from) {
        String jpql = "SELECT COUNT(a) FROM AuctionEntity a WHERE a.status = :status";
        if (from != null) {
            jpql += " AND a.createdAt >= :from";
        }

        var query = em.createQuery(jpql, Long.class);
        query.setParameter("status", AuctionStatus.ENDED);
        if (from != null) {
            query.setParameter("from", from);
        }
        long result = query.getSingleResult();
        log.info("countCompletedAuctions: from={}, status={}, result={}", from, AuctionStatus.ENDED, result);
        return result;
    }

    @Override
    public double getAvgBidCount(LocalDateTime from) {
        String jpql = "SELECT COALESCE(AVG(a.totalBidCount), 0) FROM AuctionEntity a";
        if (from != null) {
            jpql += " WHERE a.createdAt >= :from";
        }

        var query = em.createQuery(jpql, Double.class);
        if (from != null) {
            query.setParameter("from", from);
        }
        Double result = query.getSingleResult();
        log.info("getAvgBidCount: from={}, result={}", from, result);
        return result != null ? result : 0.0;
    }

    @Override
    public double getAvgPriceIncreaseRate(LocalDateTime from) {
        // 낙찰된 경매의 평균 가격 상승률
        // (currentPrice - startPrice) / startPrice * 100
        String jpql = "SELECT COALESCE(AVG((a.currentPrice - a.startPrice) * 100.0 / a.startPrice), 0) "
                + "FROM AuctionEntity a WHERE a.status = :status AND a.startPrice > 0";
        if (from != null) {
            jpql += " AND a.createdAt >= :from";
        }

        var query = em.createQuery(jpql, Double.class);
        query.setParameter("status", AuctionStatus.ENDED);
        if (from != null) {
            query.setParameter("from", from);
        }
        Double result = query.getSingleResult();
        log.info("getAvgPriceIncreaseRate: from={}, result={}", from, result);
        return result != null ? result : 0.0;
    }

    @Override
    public long countExtendedAuctions(LocalDateTime from) {
        String jpql = "SELECT COUNT(a) FROM AuctionEntity a WHERE a.extensionCount > 0";
        if (from != null) {
            jpql += " AND a.createdAt >= :from";
        }

        var query = em.createQuery(jpql, Long.class);
        if (from != null) {
            query.setParameter("from", from);
        }
        return query.getSingleResult();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<HourlyBidCount> getHourlyBidCounts(LocalDateTime from) {
        String sql = "SELECT HOUR(b.created_at) as hour, COUNT(*) as count FROM bid b";
        if (from != null) {
            sql += " WHERE b.created_at >= :from";
        }
        sql += " GROUP BY HOUR(b.created_at) ORDER BY hour";

        log.info("getHourlyBidCounts SQL: {}, from: {}", sql, from);

        var query = em.createNativeQuery(sql);
        if (from != null) {
            query.setParameter("from", from);
        }

        List<Object[]> results = query.getResultList();
        log.info("getHourlyBidCounts results size: {}", results.size());

        return results.stream()
                .map(row -> new HourlyBidCount(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue()
                ))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DailyCount> getDailyNewAuctions(LocalDateTime from) {
        String sql = "SELECT DATE(a.created_at) as date, COUNT(*) as count FROM auction a";
        if (from != null) {
            sql += " WHERE a.created_at >= :from";
        }
        sql += " GROUP BY DATE(a.created_at) ORDER BY date";

        var query = em.createNativeQuery(sql);
        if (from != null) {
            query.setParameter("from", from);
        }

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(row -> new DailyCount(
                        ((java.sql.Date) row[0]).toLocalDate(),
                        ((Number) row[1]).longValue()
                ))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DailyCount> getDailyCompletedAuctions(LocalDateTime from) {
        String sql = "SELECT DATE(a.actual_end_time) as date, COUNT(*) as count FROM auction a "
                + "WHERE a.status = 'ENDED' AND a.actual_end_time IS NOT NULL";
        if (from != null) {
            sql += " AND a.actual_end_time >= :from";
        }
        sql += " GROUP BY DATE(a.actual_end_time) ORDER BY date";

        var query = em.createNativeQuery(sql);
        if (from != null) {
            query.setParameter("from", from);
        }

        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream()
                .filter(row -> row[0] != null)
                .map(row -> new DailyCount(
                        ((java.sql.Date) row[0]).toLocalDate(),
                        ((Number) row[1]).longValue()
                ))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DailyCount> getDailyBids(LocalDateTime from) {
        String sql = "SELECT DATE(b.created_at) as date, COUNT(*) as count FROM bid b";
        if (from != null) {
            sql += " WHERE b.created_at >= :from";
        }
        sql += " GROUP BY DATE(b.created_at) ORDER BY date";

        var query = em.createNativeQuery(sql);
        if (from != null) {
            query.setParameter("from", from);
        }

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(row -> new DailyCount(
                        ((java.sql.Date) row[0]).toLocalDate(),
                        ((Number) row[1]).longValue()
                ))
                .collect(Collectors.toList());
    }
}
