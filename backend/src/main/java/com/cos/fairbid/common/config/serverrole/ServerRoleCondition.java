package com.cos.fairbid.common.config.serverrole;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * server.role 기반 빈 활성화 조건
 *
 * server.role 값에 따라 빈을 선택적으로 로딩한다.
 * - "all" (기본값): 모든 빈 활성화 (로컬 개발 환경)
 * - "api": REST Controller + Redis Pub/Sub Publisher만 활성화
 * - "ws": WebSocket Config + Redis Pub/Sub Subscriber만 활성화
 *
 * @see EnabledOnRole 이 Condition을 사용하는 어노테이션
 */
public class ServerRoleCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // @EnabledOnRole 어노테이션의 속성 읽기
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnabledOnRole.class.getName());
        if (attrs == null) {
            return true;
        }

        String[] allowedRoles = (String[]) attrs.get("value");
        String currentRole = context.getEnvironment().getProperty("server.role", "all");

        for (String role : allowedRoles) {
            if (role.equals(currentRole)) {
                return true;
            }
        }
        return false;
    }
}
