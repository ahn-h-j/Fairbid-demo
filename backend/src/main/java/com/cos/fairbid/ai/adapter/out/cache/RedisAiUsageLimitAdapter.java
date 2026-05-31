package com.cos.fairbid.ai.adapter.out.cache;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.out.AiUsageLimitPort;

/**
 * Redis 기반 AI 사용 횟수 카운터 어댑터.
 *
 * 키 구조 (날짜별):
 * - 사용자별: ai:usage:user:{userId}:{yyyyMMdd}
 * - 전역:    ai:usage:global:{yyyyMMdd}
 *
 * 날짜를 키에 포함해 자정이 지나면 자연스럽게 새 카운터가 시작된다.
 * TTL 2일을 함께 걸어 과거 키가 영원히 남지 않게 한다.
 *
 * 장애 격리(fail-open): Redis 조회 실패 시 0을 반환해 한도 검사를 통과시키고,
 * 적립 실패는 무시한다. 데모 동작이 Redis 장애로 멈추지 않게 하기 위함이다.
 * (비용 방어보다 데모 가용성을 우선한다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAiUsageLimitAdapter implements AiUsageLimitPort {

    private static final String USER_KEY_PREFIX = "ai:usage:user:";
    private static final String GLOBAL_KEY_PREFIX = "ai:usage:global:";
    private static final Duration TTL = Duration.ofDays(2);
    // 서버 시간 기준 날짜 (클라이언트 시간 신뢰 금지 규칙 준수)
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;

    @Override
    public long getUserDailyCount(Long userId) {
        return readCount(userKey(userId));
    }

    @Override
    public long getGlobalDailyCount() {
        return readCount(globalKey());
    }

    @Override
    public void increment(Long userId) {
        bumpWithTtl(userKey(userId));
        bumpWithTtl(globalKey());
    }

    /**
     * 키 값을 1 증가시키고, 처음 생성된 키(증가 후 값이 1)면 TTL을 건다.
     */
    private void bumpWithTtl(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, TTL);
            }
        } catch (Exception e) {
            // fail-open: 적립 실패는 데모 흐름을 막지 않는다
            log.warn("AI 사용 횟수 적립 실패 - key={}, error={}", key, e.getMessage());
        }
    }

    private long readCount(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            // fail-open: 조회 실패 시 0 → 한도 검사 통과
            log.warn("AI 사용 횟수 조회 실패 - key={}, error={}", key, e.getMessage());
            return 0L;
        }
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId + ":" + today();
    }

    private String globalKey() {
        return GLOBAL_KEY_PREFIX + today();
    }

    private String today() {
        return LocalDate.now().format(DATE_FORMAT);
    }
}
