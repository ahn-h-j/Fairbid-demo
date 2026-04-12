package com.cos.fairbid.ai.adapter.out.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.ai.application.port.out.PriceCachePort;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;

/**
 * Redis 기반 AI 시세 캐시 어댑터 (Phase 2).
 *
 * 키 구조: ai:price:{category}:{productKey}:{grade}
 * 값: JSON 직렬화된 {@link CachedEntry} — low/mid/high + description + confidence.
 * TTL: 7일. 같은 상품이 주 단위 시세 변동을 자연스럽게 따라가게 한다.
 *
 * 장애 격리:
 * - Redis 장애 시 find 는 empty, save 는 no-op → 본 흐름(Claude 호출)이 계속 진행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPriceCacheAdapter implements PriceCachePort {

    private static final String KEY_PREFIX = "ai:price:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<AiAssistResult> find(String category, String productKey, String grade) {
        if (isBlank(productKey)) {
            return Optional.empty();
        }
        String key = buildKey(category, productKey, grade);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                log.debug("AI 시세 캐시 MISS - key={}", key);
                return Optional.empty();
            }
            CachedEntry entry = objectMapper.readValue(json, CachedEntry.class);
            log.info("AI 시세 캐시 HIT - key={}, mid={}", key, entry.mid);
            return Optional.of(new AiAssistResult(
                    new SuggestedPrices(entry.low, entry.mid, entry.high),
                    entry.description,
                    entry.confidence != null ? entry.confidence : "high",
                    entry.confidenceReason
            ));
        } catch (JsonProcessingException e) {
            log.warn("AI 시세 캐시 JSON 파싱 실패 - key={}, error={}", key, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI 시세 캐시 조회 실패 - key={}, error={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(String category, String productKey, String grade, AiAssistResult result) {
        if (isBlank(productKey) || result == null || result.suggestedPrices() == null) {
            return;
        }
        // low confidence 케이스는 "정확하지 않을 수 있다" 고 Claude 가 명시한 답변이므로
        // 캐시에 고정하지 않는다 — 다음 호출에서 다시 계산할 기회를 준다.
        if (result.isLowConfidence()) {
            log.debug("AI 시세 캐시 적재 건너뜀 (low confidence) - productKey={}", productKey);
            return;
        }

        String key = buildKey(category, productKey, grade);
        CachedEntry entry = new CachedEntry(
                result.suggestedPrices().low(),
                result.suggestedPrices().mid(),
                result.suggestedPrices().high(),
                result.generatedDescription(),
                result.confidence(),
                result.confidenceReason()
        );
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.info("AI 시세 캐시 적재 - key={}, mid={}", key, entry.mid);
        } catch (JsonProcessingException e) {
            log.warn("AI 시세 캐시 JSON 직렬화 실패 - key={}, error={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("AI 시세 캐시 적재 실패 - key={}, error={}", key, e.getMessage());
        }
    }

    private String buildKey(String category, String productKey, String grade) {
        String cat = category != null ? category : "NONE";
        String gd = grade != null ? grade : "NONE";
        return KEY_PREFIX + cat + ":" + productKey + ":" + gd;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Jackson 직렬화용 내부 DTO. Long/String 기본 타입만 사용. */
    static class CachedEntry {
        public Long low;
        public Long mid;
        public Long high;
        public String description;
        public String confidence;
        public String confidenceReason;

        public CachedEntry() {
        }

        public CachedEntry(Long low, Long mid, Long high, String description,
                           String confidence, String confidenceReason) {
            this.low = low;
            this.mid = mid;
            this.high = high;
            this.description = description;
            this.confidence = confidence;
            this.confidenceReason = confidenceReason;
        }
    }
}
