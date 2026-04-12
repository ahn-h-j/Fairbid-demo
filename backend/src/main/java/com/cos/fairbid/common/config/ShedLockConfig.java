package com.cos.fairbid.common.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

/**
 * ShedLock 설정 — 분산 스케줄러 락 프로바이더.
 *
 * 스케일아웃 환경에서 여러 인스턴스가 같은 `@Scheduled` 메서드를 동시에 실행하는 걸 방지한다.
 * 락 상태는 MySQL 의 `shedlock` 테이블에 저장한다 — 기존 데이터소스 재사용.
 *
 * 테이블 스키마 (ddl-auto=update 에서 자동 생성되지 않으므로 수동 스크립트로 생성):
 * <pre>
 * CREATE TABLE shedlock (
 *     name VARCHAR(64) NOT NULL,
 *     lock_until TIMESTAMP(3) NOT NULL,
 *     locked_at TIMESTAMP(3) NOT NULL,
 *     locked_by VARCHAR(255) NOT NULL,
 *     PRIMARY KEY (name)
 * );
 * </pre>
 */
@Configuration
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
