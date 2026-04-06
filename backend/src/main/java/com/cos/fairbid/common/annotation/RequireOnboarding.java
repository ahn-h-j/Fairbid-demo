package com.cos.fairbid.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 온보딩 완료 필수 어노테이션
 * 이 어노테이션이 붙은 컨트롤러 메서드는 온보딩이 완료된 사용자만 접근 가능하다.
 * JWT의 onboarded 클레임을 기반으로 판단하여 DB 조회 없이 동작한다.
 *
 * 사용 예시:
 * {@code @RequireOnboarding}
 * {@code @PostMapping}
 * public ResponseEntity<?> createAuction(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireOnboarding {
}
