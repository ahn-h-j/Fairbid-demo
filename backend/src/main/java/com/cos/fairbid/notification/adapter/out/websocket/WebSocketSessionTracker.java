package com.cos.fairbid.notification.adapter.out.websocket;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;

/**
 * WebSocket STOMP 세션 수 추적
 *
 * SessionConnectEvent / SessionDisconnectEvent를 감지하여
 * 현재 서버에 연결된 WebSocket 커넥션 수를 추적한다.
 *
 * 노출 경로:
 * 1. /actuator/wsconnections — 서버 IP와 함께 JSON 응답 (Step 5 부하테스트 용도)
 * 2. Prometheus metric `fairbid_websocket_active_connections` — AI 모니터링 + Grafana 용도
 */
@Component
@EnabledOnRole({"ws", "all"})
public class WebSocketSessionTracker {

    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public WebSocketSessionTracker(MeterRegistry meterRegistry) {
        // Prometheus Gauge 등록 — AtomicInteger 바인딩으로 값이 자동 반영
        Gauge.builder("fairbid_websocket_active_connections", activeConnections, AtomicInteger::get)
                .description("현재 서버에 연결된 활성 WebSocket(STOMP) 세션 수")
                .register(meterRegistry);
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        activeConnections.incrementAndGet();
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        activeConnections.decrementAndGet();
    }

    /**
     * 현재 서버의 활성 WebSocket 커넥션 수 반환
     */
    public int getActiveConnectionCount() {
        return activeConnections.get();
    }
}
