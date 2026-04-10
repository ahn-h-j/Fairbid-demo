package com.cos.fairbid.notification.adapter.out.pubsub;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import lombok.extern.slf4j.Slf4j;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;

/**
 * Redis Pub/Sub 리스너(구독) 설정
 *
 * 경매 입찰 업데이트와 경매 종료 채널을 구독하여
 * RedisMessageSubscriber가 메시지를 수신할 수 있게 한다.
 *
 * server.role=ws 또는 all에서만 활성화.
 * API 서버에서는 Redis 구독 리스너가 불필요하다.
 */
@Slf4j
@Configuration
@EnabledOnRole({"ws", "all"})
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 구독 에러 핸들러 설정 (Sentinel 환경 디버깅용)
        container.setErrorHandler(t ->
                log.error("[Redis Pub/Sub] 구독 에러: {}", t.getMessage(), t));

        // 입찰 업데이트 채널 구독
        container.addMessageListener(subscriber,
                new ChannelTopic(RedisPubSubBroadcastAdapter.CHANNEL_BID_UPDATE));

        // 경매 종료 채널 구독
        container.addMessageListener(subscriber,
                new ChannelTopic(RedisPubSubBroadcastAdapter.CHANNEL_AUCTION_CLOSED));

        log.info("[Redis Pub/Sub] 리스너 컨테이너 설정 완료: channels=[{}, {}]",
                RedisPubSubBroadcastAdapter.CHANNEL_BID_UPDATE,
                RedisPubSubBroadcastAdapter.CHANNEL_AUCTION_CLOSED);

        return container;
    }
}
