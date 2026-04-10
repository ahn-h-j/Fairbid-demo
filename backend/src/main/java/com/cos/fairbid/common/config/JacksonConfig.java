package com.cos.fairbid.common.config;

import java.util.TimeZone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson ObjectMapper 설정
 * LocalDateTime 등 Java 8 날짜/시간 타입을 ISO 8601 문자열로 직렬화한다.
 * 서버는 UTC 시간대를 사용하며, 클라이언트에서 로컬 시간으로 변환한다.
 */
@Configuration
public class JacksonConfig {

    /**
     * ObjectMapper 빈 설정
     * - JavaTimeModule 등록: Java 8 날짜/시간 타입 지원
     * - WRITE_DATES_AS_TIMESTAMPS 비활성화: 배열 대신 ISO 문자열 형식 사용
     * - UTC 타임존 설정: 모든 날짜를 UTC로 직렬화
     *
     * @return 설정된 ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        return objectMapper;
    }
}
