package com.cos.fairbid.common.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.TimeoutOptions;

/**
 * Redis Sentinel 전용 Lettuce 클라이언트 설정 (HA Step 3)
 *
 * sentinel 프로필이 활성화되면 Lettuce 클라이언트에 아래 동작을 추가한다:
 *
 * 1. autoReconnect: failover 후 Sentinel에서 새 Master 주소를 받아 자동 재연결
 * 2. REJECT_COMMANDS: 연결 끊긴 상태에서 명령 대기 없이 즉시 에러 반환 (빠른 실패)
 * 3. fixedTimeout 3초: 커넥션/커맨드 타임아웃 상한
 * 4. MASTER: 모든 읽기/쓰기를 Master에서 처리.
 *    경매 시스템 특성상 Slave의 stale 데이터(비동기 복제 지연)를 읽으면
 *    이전 입찰가/낙찰자 등 잘못된 정보를 반환할 위험이 있으므로 Master 전용으로 설정한다.
 */
@Configuration
@Profile("sentinel")
public class RedisSentinelConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> builder
                .readFrom(ReadFrom.MASTER)
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .timeoutOptions(TimeoutOptions.builder()
                                .fixedTimeout(Duration.ofSeconds(3))
                                .build())
                        .build());
    }
}
