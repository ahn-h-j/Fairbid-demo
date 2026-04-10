package com.cos.fairbid.bid.adapter.in.monitoring;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.bid.adapter.out.stream.RedisBidStreamAdapter;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;

/**
 * Redis-RDB 입찰 정합성 모니터링 스케줄러 (load-test 프로파일 전용)
 *
 * 5초마다 Redis의 총 입찰 건수와 RDB의 총 입찰 건수를 비교하여
 * Prometheus Gauge로 노출한다. Grafana에서 두 값의 차이를 시각화하여
 * 불일치 발생 시점과 규모를 확인할 수 있다.
 *
 * Redis 입찰 수: 모든 auction:{id} 해시의 totalBidCount 합산
 * RDB 입찰 수: bid 테이블 COUNT(*)
 *
 * 프로덕션에서는 5초마다 COUNT(*) 풀 스캔이 부담이므로
 * load-test 프로파일에서만 활성화한다.
 */
@Component
@Profile("load-test")
@EnabledOnRole({"api", "all"})
@Slf4j
public class BidConsistencyChecker {

    private static final String AUCTION_KEY_PREFIX = "auction:";
    private static final String CLOSING_QUEUE_KEY = "auction:closing";

    private final StringRedisTemplate redisTemplate;
    private final BidRepositoryPort bidRepositoryPort;

    private static final String STREAM_GROUP = "bid-rdb-sync-group";

    /** Gauge에 바인딩할 AtomicLong (스케줄러가 주기적으로 갱신) */
    private final AtomicLong redisCount = new AtomicLong(0);
    private final AtomicLong rdbCount = new AtomicLong(0);
    private final AtomicLong inconsistencyCount = new AtomicLong(0);
    private final AtomicLong streamPendingCount = new AtomicLong(0);

    public BidConsistencyChecker(
            StringRedisTemplate redisTemplate,
            BidRepositoryPort bidRepositoryPort,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.bidRepositoryPort = bidRepositoryPort;

        // Gauge 등록 (AtomicLong 바인딩으로 값이 자동 반영)
        Gauge.builder("fairbid_bid_redis_count", redisCount, AtomicLong::get)
                .description("Redis에 기록된 총 입찰 수 (auction hash totalBidCount 합산)")
                .register(meterRegistry);

        Gauge.builder("fairbid_bid_rdb_count", rdbCount, AtomicLong::get)
                .description("RDB에 저장된 총 입찰 수 (bid 테이블 COUNT)")
                .register(meterRegistry);

        Gauge.builder("fairbid_bid_inconsistency_count", inconsistencyCount, AtomicLong::get)
                .description("Redis-RDB 입찰 건수 차이 (불일치)")
                .register(meterRegistry);

        Gauge.builder("fairbid_stream_pending_count", streamPendingCount, AtomicLong::get)
                .description("Redis Stream PENDING 메시지 수 (미처리 RDB 동기화 건수)")
                .register(meterRegistry);
    }

    /**
     * 5초마다 Redis vs RDB 입찰 건수를 비교한다.
     * 테스트 환경에서 실시간 모니터링 용도이며, 프로덕션에서는 주기를 늘려야 한다.
     */
    @Scheduled(fixedRate = 5000)
    public void checkConsistency() {
        // Redis 카운트는 DB 장애와 무관하게 항상 갱신 (장애 격리)
        long redis = 0;
        try {
            redis = countRedisBids();
            redisCount.set(redis);
        } catch (Exception e) {
            log.error("Redis 입찰 수 조회 실패: {}", e.getMessage());
        }

        // RDB 카운트 갱신 (DB 다운 시 실패해도 Redis 카운트에 영향 없음)
        try {
            long rdb = bidRepositoryPort.countAll();
            rdbCount.set(rdb);
            long diff = redis - rdb;
            inconsistencyCount.set(diff);

            if (diff != 0) {
                log.warn("Redis-RDB 입찰 불일치 감지: Redis={}, RDB={}, 차이={}", redis, rdb, diff);
            }
        } catch (Exception e) {
            // DB 다운 시 - 마지막 RDB 값 기준으로 불일치 갱신
            inconsistencyCount.set(redis - rdbCount.get());
            log.error("RDB 입찰 수 조회 실패 (DB 다운 추정): {}", e.getMessage());
        }

        // Stream PENDING 메시지 수 조회
        checkStreamPending();
    }

    /**
     * Redis Stream의 PENDING 메시지 수를 조회한다.
     * PENDING이 누적되면 DB 장애 등으로 RDB 동기화가 지연되고 있음을 의미한다.
     */
    private void checkStreamPending() {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(RedisBidStreamAdapter.STREAM_KEY, STREAM_GROUP);
            if (summary != null) {
                streamPendingCount.set(summary.getTotalPendingMessages());
            }
        } catch (Exception e) {
            // Stream 또는 Group이 아직 없는 경우 무시
            log.debug("Stream PENDING 조회 실패 (정상 가능): {}", e.getMessage());
        }
    }

    /**
     * Redis의 모든 auction:{id} 해시에서 totalBidCount를 합산한다.
     * SCAN 명령어를 사용하여 blocking 없이 순회한다.
     *
     * @return Redis 기준 총 입찰 수
     */
    private long countRedisBids() {
        long total = 0;
        ScanOptions options = ScanOptions.scanOptions()
                .match(AUCTION_KEY_PREFIX + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // auction:closing (Sorted Set)은 건너뛴다
                if (CLOSING_QUEUE_KEY.equals(key)) {
                    continue;
                }
                Object countValue = redisTemplate.opsForHash().get(key, "totalBidCount");
                if (countValue != null) {
                    total += Long.parseLong(countValue.toString());
                }
            }
        }
        return total;
    }
}
