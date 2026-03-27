package com.cos.fairbid.notification.adapter.out.websocket;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket STOMP 세션 수 추적
 *
 * SessionConnectEvent / SessionDisconnectEvent를 감지하여
 * 현재 서버에 연결된 WebSocket 커넥션 수를 추적한다.
 *
 * Step 5 테스트에서 서버별 커넥션 쏠림을 측정하기 위해 사용.
 * /actuator/ws-connections 엔드포인트에서 이 값을 노출한다.
 */
@Component
@EnabledOnRole({"ws", "all"})
public class WebSocketSessionTracker {

    private final AtomicInteger activeConnections = new AtomicInteger(0);

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
