package com.cos.fairbid.common.config.serverrole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * server.role 값에 따라 빈 활성화를 제어하는 어노테이션
 *
 * 사용 예:
 * - @EnabledOnRole({"api", "all"})  → API 서버 + 로컬 개발에서만 활성화
 * - @EnabledOnRole({"ws", "all"})   → WebSocket 서버 + 로컬 개발에서만 활성화
 *
 * server.role 미설정 시 기본값 "all"로 동작하여 모든 빈이 활성화된다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ServerRoleCondition.class)
public @interface EnabledOnRole {

    /**
     * 이 빈이 활성화될 server.role 값 목록
     * 예: {"api", "all"}, {"ws", "all"}
     */
    String[] value();
}
