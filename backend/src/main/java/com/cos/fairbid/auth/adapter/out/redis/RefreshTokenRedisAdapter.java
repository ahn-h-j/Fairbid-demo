package com.cos.fairbid.auth.adapter.out.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.auth.application.port.out.RefreshTokenPort;

/**
 * Refresh Token Redis 어댑터
 * Redis를 사용하여 Refresh Token을 SHA-256 해시로 관리한다.
 *
 * - Key 형식: refresh:{userId}
 * - Value: Refresh Token의 SHA-256 해시
 * - TTL: Refresh Token 만료 시간과 동일 (2주)
 *
 * 해시 저장 이유: Redis 유출 시 토큰 직접 사용을 방지한다.
 * 단일 세션 정책: 사용자당 하나의 Refresh Token만 저장된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRedisAdapter implements RefreshTokenPort {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long userId, String refreshToken, long ttlSeconds) {
        String key = generateKey(userId);
        String hashed = hash(refreshToken);
        redisTemplate.opsForValue().set(key, hashed, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Refresh Token 저장 완료: userId={}", userId);
    }

    @Override
    public Optional<String> find(Long userId) {
        String key = generateKey(userId);
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    @Override
    public void delete(Long userId) {
        String key = generateKey(userId);
        redisTemplate.delete(key);
        log.debug("Refresh Token 삭제 완료: userId={}", userId);
    }

    @Override
    public boolean matches(Long userId, String refreshToken) {
        return find(userId)
                .map(storedHash -> storedHash.equals(hash(refreshToken)))
                .orElse(false);
    }

    /**
     * Redis Key를 생성한다.
     *
     * @param userId 사용자 ID
     * @return "refresh:{userId}" 형식의 키
     */
    private String generateKey(Long userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * 토큰을 SHA-256으로 해시한다.
     * Redis 유출 시 토큰 직접 사용을 방지한다.
     *
     * @param token 원본 토큰
     * @return Base64URL 인코딩된 해시 문자열
     */
    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
