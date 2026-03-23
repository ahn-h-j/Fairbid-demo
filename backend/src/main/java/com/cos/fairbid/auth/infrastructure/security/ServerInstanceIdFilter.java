package com.cos.fairbid.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;

/**
 * 응답 헤더에 서버 인스턴스 식별 정보를 추가하는 필터 (load-test 프로필 전용)
 *
 * WebSocket 스케일아웃 테스트 시, 어떤 서버에 요청이 도달했는지 확인하기 위해 사용한다.
 * - X-Server-Id: 호스트명 (EC2에서는 인스턴스 ID와 유사)
 * - X-Server-Ip: 서버의 private IP
 *
 * 프로덕션 환경에서는 절대 활성화하면 안 된다. 서버 내부 정보 노출 위험.
 */
@Component
@Profile("load-test")
public class ServerInstanceIdFilter extends OncePerRequestFilter {

    private final String hostname;
    private final String hostIp;

    public ServerInstanceIdFilter() {
        String name;
        String ip;
        try {
            InetAddress addr = InetAddress.getLocalHost();
            name = addr.getHostName();
            ip = addr.getHostAddress();
        } catch (Exception e) {
            name = "unknown";
            ip = "unknown";
        }
        this.hostname = name;
        this.hostIp = ip;
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
