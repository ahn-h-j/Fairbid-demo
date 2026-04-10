package com.cos.fairbid.common.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.user.domain.exception.OnboardingRequiredException;

/**
 * 온보딩 상태 검사 AOP Aspect
 * {@code @RequireOnboarding} 어노테이션이 선언된 메서드 실행 전에
 * 현재 사용자의 온보딩 완료 상태를 확인한다.
 *
 * JWT의 onboarded 클레임을 기반으로 판단하므로 DB 조회가 발생하지 않는다.
 * 트랜잭션 시작 전에 실행되어 불필요한 DB 커넥션 점유를 방지한다.
 */
@Aspect
@Component
@Order(1)
public class OnboardingCheckAspect {

    /**
     * @RequireOnboarding 어노테이션이 선언된 메서드 실행 전에 온보딩 상태를 확인한다.
     * 온보딩 미완료 시 OnboardingRequiredException(403)을 던진다.
     */
    @Before("@annotation(com.cos.fairbid.common.annotation.RequireOnboarding)")
    public void checkOnboarding() {
        if (!SecurityUtils.isOnboarded()) {
            throw OnboardingRequiredException.create();
        }
    }
}
