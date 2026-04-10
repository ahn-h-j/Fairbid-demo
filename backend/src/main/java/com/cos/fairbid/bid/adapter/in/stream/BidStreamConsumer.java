package com.cos.fairbid.bid.adapter.in.stream;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.bid.adapter.out.stream.RedisBidStreamAdapter;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;

/**
 * Redis Stream 기반 입찰 RDB 동기화 컨슈머
 *
 * Consumer Group을 통해 Redis Stream 메시지를 소비하고,
 * 별도 빈(BidStreamMessageHandler)에서 RDB 저장 후 ACK를 보내는 Inbound Adapter.
 *
 * 핵심 동작:
 * 1. 새 메시지 수신 → Handler에서 RDB 저장 (트랜잭션 커밋) → ACK (정상 흐름)
 * 2. RDB 저장 실패 → ACK 안 함 → PENDING에 남음 (장애 시)
 * 3. @Scheduled로 PENDING 메시지 주기적 재처리 (복구 시)
 *
 * @Async 대비 장점:
 * - DB 장애 시에도 호출 스레드(입찰 API) 블로킹 없음
 * - 앱 종료 시에도 메시지가 Redis에 남아 재시작 후 재처리
 * - CallerRunsPolicy 문제 없음 (스트림 발행은 O(1))
 */
@Component
@Slf4j
@EnabledOnRole({"api", "all"})
public class BidStreamConsumer implements DisposableBean {

    private static final String STREAM_KEY = RedisBidStreamAdapter.STREAM_KEY;
    private static final String GROUP_NAME = "bid-rdb-sync-group";
    /** PENDING 메시지 재처리 대상 기준 시간 (이 시간 이상 ACK 안 된 메시지) */
    private static final Duration PENDING_TIMEOUT = Duration.ofSeconds(30);
    /** 병렬 Consumer 수 (Consumer Group 내에서 메시지를 분산 처리) */
    private static final int CONSUMER_COUNT = 10;

    private final String consumerName;
    private final StringRedisTemplate redisTemplate;
    private final BidStreamMessageHandler messageHandler;
    private final Timer rdbSyncTimer;
    private final Counter consumeSuccessCounter;
    private final Counter consumeFailCounter;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    /** 리스너 컨테이너용 스레드 풀 (destroy()에서 명시적 종료) */
    private ThreadPoolTaskExecutor executor;

    public BidStreamConsumer(
            StringRedisTemplate redisTemplate,
            BidStreamMessageHandler messageHandler,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.messageHandler = messageHandler;
        // 인스턴스별 고유 컨슈머 이름 (다중 인스턴스 대비)
        this.consumerName = "consumer-" + UUID.randomUUID().toString().substring(0, 8);

        this.rdbSyncTimer = Timer.builder("fairbid_bid_rdb_sync_seconds")
                .description("RDB 동기화 소요 시간")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
        this.consumeSuccessCounter = Counter.builder("fairbid_stream_consume_total")
                .tag("result", "success")
                .description("Stream 메시지 소비 성공 건수")
                .register(meterRegistry);
        this.consumeFailCounter = Counter.builder("fairbid_stream_consume_total")
                .tag("result", "fail")
                .description("Stream 메시지 소비 실패 건수")
                .register(meterRegistry);
    }

    /**
     * 애플리케이션 시작 시 Consumer Group 생성 및 리스너 컨테이너 시작
     */
    @PostConstruct
    public void init() {
        createConsumerGroupIfNotExists();
        startListenerContainer();
        log.info("BidStreamConsumer 시작: group={}, consumer={}", GROUP_NAME, consumerName);
    }

    /**
     * Consumer Group이 없으면 생성한다.
     * 스트림이 없는 경우 더미 메시지를 추가하여 스트림을 먼저 생성한다.
     */
    private void createConsumerGroupIfNotExists() {
        try {
            // 스트림이 없으면 더미 메시지로 생성 후 즉시 삭제 (MKSTREAM 대체)
            Boolean exists = redisTemplate.hasKey(STREAM_KEY);
            if (Boolean.FALSE.equals(exists)) {
                RecordId dummyId = redisTemplate.opsForStream()
                        .add(STREAM_KEY, Map.of("_init", "true"));
                if (dummyId != null) {
                    redisTemplate.opsForStream().delete(STREAM_KEY, dummyId);
                }
                log.info("Stream 키 생성: {}", STREAM_KEY);
            }

            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            log.info("Consumer Group 생성: stream={}, group={}", STREAM_KEY, GROUP_NAME);
        } catch (RedisSystemException e) {
            // BUSYGROUP: 이미 존재하는 경우 정상
            if (e.getCause() != null && e.getCause().getMessage() != null
                    && e.getCause().getMessage().contains("BUSYGROUP")) {
                log.info("Consumer Group 이미 존재: stream={}, group={}", STREAM_KEY, GROUP_NAME);
            } else {
                throw e;
            }
        }
    }

    /**
     * StreamMessageListenerContainer를 설정하고 시작한다.
     * Consumer Group 내에 여러 Consumer를 등록하여 병렬 처리한다.
     * 각 Consumer는 독립 스레드에서 Stream을 폴링하고, Consumer Group이
     * 메시지를 자동으로 분배하므로 중복 처리 없이 병렬성을 확보한다.
     */
    private void startListenerContainer() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CONSUMER_COUNT);
        executor.setMaxPoolSize(CONSUMER_COUNT * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bid-stream-consumer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .batchSize(50)
                        .pollTimeout(Duration.ofSeconds(1))
                        .executor(executor)
                        .errorHandler(e -> log.error("Stream 리스너 에러: {}", e.getMessage()))
                        .build();

        container = StreamMessageListenerContainer.create(
                redisTemplate.getConnectionFactory(), options);

        // 병렬 Consumer 등록: 각 Consumer가 독립적으로 Stream을 폴링
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            String name = consumerName + "-" + i;
            container.receive(
                    Consumer.from(GROUP_NAME, name),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    this::onMessage
            );
        }

        container.start();
    }

    /**
     * 메시지 수신 콜백
     * Handler 빈에서 RDB 저장 (트랜잭션 커밋) 후 수동 ACK한다.
     * 실패 시 ACK하지 않아 PENDING에 남는다.
     *
     * ACK 타이밍: Handler의 @Transactional 메서드가 반환되면 트랜잭션이 커밋된 상태이므로
     * 이후 ACK를 전송하여 "커밋 후 ACK" 순서를 보장한다.
     */
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        String recordId = message.getId().getValue();

        try {
            rdbSyncTimer.record(() -> {
                // Handler 빈 호출 → Spring 프록시 경유 → @Transactional 정상 동작
                messageHandler.handle(body, recordId);
            });

            // 트랜잭션 커밋 확인 후 ACK
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
            consumeSuccessCounter.increment();
            log.debug("메시지 처리 완료: recordId={}", recordId);
        } catch (Exception e) {
            consumeFailCounter.increment();
            // ACK하지 않으면 PENDING 목록에 남아 retryPendingMessages()에서 재처리됨
            log.error("메시지 처리 실패 (PENDING 유지): recordId={}, error={}",
                    recordId, e.getMessage());
        }
    }

    /**
     * PENDING 메시지 재처리 스케줄러
     *
     * 30초마다 실행되며, PENDING_TIMEOUT 이상 ACK되지 않은 메시지를
     * XCLAIM으로 가져와 재처리한다. DB 복구 후 밀린 메시지가 자동으로
     * 소진되는 핵심 메커니즘이다.
     */
    @Scheduled(fixedRate = 30000)
    public void retryPendingMessages() {
        try {
            // XPENDING으로 처리 안 된 메시지 조회 (최대 50건)
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 50);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            long retryCount = 0;
            for (PendingMessage pending : pendingMessages) {
                // PENDING_TIMEOUT 이상 지난 메시지만 재처리
                if (pending.getElapsedTimeSinceLastDelivery().compareTo(PENDING_TIMEOUT) < 0) {
                    continue;
                }

                // XCLAIM으로 이 컨슈머에게 소유권 이전
                @SuppressWarnings("unchecked")
                List<MapRecord<String, String, String>> claimed = (List<MapRecord<String, String, String>>) (List<?>)
                        redisTemplate.opsForStream()
                                .claim(STREAM_KEY, GROUP_NAME, consumerName,
                                        PENDING_TIMEOUT, pending.getId());

                for (MapRecord<String, String, String> message : claimed) {
                    onMessage(message);
                    retryCount++;
                }
            }

            if (retryCount > 0) {
                log.info("PENDING 메시지 재처리 완료: {}건", retryCount);
            }
        } catch (Exception e) {
            log.error("PENDING 메시지 재처리 중 에러: {}", e.getMessage());
        }
    }

    /**
     * 애플리케이션 종료 시 리스너 컨테이너와 스레드 풀을 정지한다.
     * 처리 중이던 메시지는 ACK되지 않아 PENDING에 남고,
     * 재시작 시 retryPendingMessages()에서 재처리된다.
     */
    @Override
    public void destroy() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("BidStreamConsumer 리스너 컨테이너 종료: consumer={}", consumerName);
        }
        if (executor != null) {
            executor.shutdown();
            log.info("BidStreamConsumer 스레드 풀 종료");
        }
    }
}
