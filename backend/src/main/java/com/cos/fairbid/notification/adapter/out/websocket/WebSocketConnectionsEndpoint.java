package com.cos.fairbid.notification.adapter.out.websocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import com.cos.fairbid.common.config.serverrole.EnabledOnRole;

/**
 * 커스텀 Actuator 엔드포인트: /actuator/wsconnections
 *
 * 현재 서버의 WebSocket 커넥션 수와 서버 정보를 반환한다.
 * Step 5 테스트에서 서버별 커넥션 쏠림을 측정하기 위해 사용.
 *
 * EC2 환경에서는 메타데이터 API로 호스트 IP를 조회한다.
 * Docker 컨테이너 안에서도 EC2 메타데이터는 호스트 EC2의 정보를 반환하므로
 * InetAddress.getLocalHost()의 컨테이너 내부 IP 문제를 회피한다.
 *
 * 응답 예시:
 * {
 *   "serverIp": "172.31.15.244",
 *   "activeConnections": 100
 * }
 */
@Component
@Endpoint(id = "wsconnections")
@EnabledOnRole({"ws", "all"})
public class WebSocketConnectionsEndpoint {

    private final WebSocketSessionTracker sessionTracker;
    private final String serverIp;

    public WebSocketConnectionsEndpoint(WebSocketSessionTracker sessionTracker) {
        this.sessionTracker = sessionTracker;
        // 시작 시 한 번만 조회 (EC2 메타데이터 → fallback: InetAddress)
        this.serverIp = resolveServerIp();
    }

    @ReadOperation
    public Map<String, Object> connections() {
        return Map.of(
                "serverIp", serverIp,
                "activeConnections", sessionTracker.getActiveConnectionCount()
        );
    }

    /**
     * EC2 메타데이터 API로 호스트 private IP를 조회한다.
     * EC2가 아닌 환경(로컬 등)에서는 InetAddress.getLocalHost()로 fallback.
     */
    private String resolveServerIp() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://169.254.169.254/latest/meta-data/local-ipv4").toURL().openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (IOException e) {
            // EC2가 아닌 환경 → fallback
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                return "unknown";
            }
        }
    }
}
