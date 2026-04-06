package com.cos.fairbid.notification.adapter.out.redis;

import com.cos.fairbid.notification.application.port.out.NotificationStoragePort;
import com.cos.fairbid.notification.domain.InAppNotification;
import com.cos.fairbid.notification.domain.NotificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 기반 알림 저장소 어댑터
 * 알림을 Redis에 저장하고 24시간 후 자동 삭제한다.
 */
@Slf4j
@Component
public class NotificationRedisAdapter implements NotificationStoragePort {

    private static final String NOTIFICATION_KEY_PREFIX = "notifications:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void save(Long userId, InAppNotification notification) {
        String key = NOTIFICATION_KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(toMap(notification));
            // List의 앞에 추가 (최신순)
            redisTemplate.opsForList().leftPush(key, json);
            // TTL 설정 (키가 없을 때만 적용되므로 매번 갱신)
            redisTemplate.expire(key, TTL);

            // 최대 50개 유지 (오래된 알림 삭제)
            redisTemplate.opsForList().trim(key, 0, 49);

            log.debug("알림 저장 완료 - userId: {}, notificationId: {}", userId, notification.getId());
        } catch (JsonProcessingException e) {
            log.error("알림 저장 실패 - userId: {}, error: {}", userId, e.getMessage());
            throw new RuntimeException("알림 저장 중 직렬화 오류 발생", e);
        }
    }

    @Override
    public List<InAppNotification> findByUserId(Long userId) {
        String key = NOTIFICATION_KEY_PREFIX + userId;
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);

        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }

        List<InAppNotification> notifications = new ArrayList<>();
        for (String json : jsonList) {
            try {
                notifications.add(fromJson(json));
            } catch (Exception e) {
                log.warn("알림 파싱 실패: {}", e.getMessage());
            }
        }
        return notifications;
    }

    @Override
    public void markAsRead(Long userId, String notificationId) {
        String key = NOTIFICATION_KEY_PREFIX + userId;
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);

        if (jsonList == null || jsonList.isEmpty()) {
            return;
        }

        for (int i = 0; i < jsonList.size(); i++) {
            try {
                InAppNotification notification = fromJson(jsonList.get(i));
                if (notification.getId().equals(notificationId)) {
                    InAppNotification read = notification.markAsRead();
                    String updatedJson = objectMapper.writeValueAsString(toMap(read));
                    redisTemplate.opsForList().set(key, i, updatedJson);
                    break;
                }
            } catch (Exception e) {
                log.warn("알림 읽음 처리 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    public int countUnread(Long userId) {
        List<InAppNotification> notifications = findByUserId(userId);
        return (int) notifications.stream().filter(n -> !n.isRead()).count();
    }

    /**
     * InAppNotification을 Map으로 변환 (JSON 직렬화용)
     */
    private Map<String, Object> toMap(InAppNotification notification) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", notification.getId());
        map.put("type", notification.getType().name());
        map.put("title", notification.getTitle());
        map.put("body", notification.getBody());
        map.put("auctionId", notification.getAuctionId());
        map.put("tradeId", notification.getTradeId());
        map.put("read", notification.isRead());
        map.put("createdAt", notification.getCreatedAt().toString());
        return map;
    }

    /**
     * JSON 문자열을 InAppNotification으로 변환
     */
    @SuppressWarnings("unchecked")
    private InAppNotification fromJson(String json) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        return InAppNotification.builder()
                .id((String) map.get("id"))
                .type(NotificationType.valueOf((String) map.get("type")))
                .title((String) map.get("title"))
                .body((String) map.get("body"))
                .auctionId(map.get("auctionId") != null ? ((Number) map.get("auctionId")).longValue() : null)
                .tradeId(map.get("tradeId") != null ? ((Number) map.get("tradeId")).longValue() : null)
                .read((Boolean) map.get("read"))
                .createdAt(LocalDateTime.parse((String) map.get("createdAt")))
                .build();
    }
}
