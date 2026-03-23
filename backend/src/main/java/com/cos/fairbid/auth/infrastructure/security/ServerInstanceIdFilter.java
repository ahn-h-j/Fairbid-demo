package com.cos.fairbid.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;

/**
 * 응답 헤더에 서버 인스턴스 식별 정보를 추가하는 필터 (load-test 프로필 전용)
 *
 * WebSocket 스케일아웃 테스트 시, 어떤 서버에 요청이 도달했는지 확인하기 위해 사용한다.
 * - X-Server-Id: EC2 인스턴스 ID (Docker 안에서도 EC2 메타데이터로 조회)
 * - X-Server-Ip: EC2 private IP
 *
 * 프로덕션 환경에서는 절대 활성화하면 안 된다. 서버 내부 정보 노출 위험.
 */
@Component
@Profile("load-test")
public class ServerInstanceIdFilter extends OncePerRequestFilter {

    private final String hostname;
    private final String hostIp;

    public ServerInstanceIdFilter() {
        // EC2 메타데이터 API로 인스턴스 정보 조회 (Docker 안에서도 호스트 EC2 정보를 가져옴)
        this.hostname = fetchMetadata("http://169.254.169.254/latest/meta-data/instance-id");
        this.hostIp = fetchMetadata("http://169.254.169.254/latest/meta-data/local-ipv4");
    }

    /**
     * EC2 인스턴스 메타데이터 조회
     * 로컬(비EC2)에서는 fallback으로 hostname/IP 반환
     */
    private String fetchMetadata(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            // EC2가 아닌 환경 (로컬 등) → fallback
            try {
                InetAddress addr = InetAddress.getLocalHost();
                return url.contains("instance-id") ? addr.getHostName() : addr.getHostAddress();
            } catch (Exception ex) {
                return "unknown";
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Server-Id", hostname);
        response.setHeader("X-Server-Ip", hostIp);
        filterChain.doFilter(request, response);
    }
}
