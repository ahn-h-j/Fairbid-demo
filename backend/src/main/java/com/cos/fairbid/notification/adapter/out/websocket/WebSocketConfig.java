package com.cos.fairbid.notification.adapter.out.websocket;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 설정
 * 클라이언트는 /ws 엔드포인트로 연결하고, /topic/auctions/{auctionId}를 구독
 *
 * server.role=ws 또는 all(기본)에서만 활성화.
 * API 전용 서버에서는 WebSocket 브로커를 띄울 필요가 없다.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnabledOnRole({"ws", "all"})
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 메시지 브로커 설정
     * - /topic: 구독 대상 prefix (서버 → 클라이언트)
     * - /app: 메시지 전송 prefix (클라이언트 → 서버, 현재 미사용)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 구독할 수 있는 prefix 설정
        registry.enableSimpleBroker("/topic");

        // 클라이언트가 서버로 메시지를 보낼 때 사용하는 prefix (현재 미사용)
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * STOMP 엔드포인트 등록
     * 클라이언트는 /ws로 WebSocket 연결
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // CORS 허용 (개발 환경)
                .withSockJS();  // SockJS 폴백 지원
    }
}
