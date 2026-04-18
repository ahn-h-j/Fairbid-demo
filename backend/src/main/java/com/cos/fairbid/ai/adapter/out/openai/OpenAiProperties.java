package com.cos.fairbid.ai.adapter.out.openai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * OpenAI API 설정 프로퍼티.
 * application.yml 의 ai.openai.* 항목을 바인딩한다.
 *
 * ai.provider=openai 일 때만 빈 등록 — Claude/OpenAI 어댑터가 동시에 올라오지 않도록 분리.
 */
@Getter
@Setter
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@ConfigurationProperties(prefix = "ai.openai")
public class OpenAiProperties {

    /** OpenAI API Key (sk-...) — 환경변수 OPENAI_API_KEY 로 주입 권장 */
    private String apiKey;

    /** OpenAI 모델 ID */
    private String model = "gpt-4.1-mini";

    /** OpenAI API base URL */
    private String baseUrl = "https://api.openai.com";

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 5_000;

    /** 응답 대기 타임아웃 (ms) */
    private int readTimeoutMs = 60_000;

    /** 응답 토큰 상한 */
    private int maxTokens = 2000;
}
