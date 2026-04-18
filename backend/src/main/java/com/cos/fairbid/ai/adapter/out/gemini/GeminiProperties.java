package com.cos.fairbid.ai.adapter.out.gemini;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Google Gemini API 설정 프로퍼티.
 * application.yml 의 ai.gemini.* 항목을 바인딩한다.
 *
 * ai.provider=gemini 일 때만 빈 등록.
 */
@Getter
@Setter
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
@ConfigurationProperties(prefix = "ai.gemini")
public class GeminiProperties {

    /** Google AI Studio API Key — 환경변수 GEMINI_API_KEY 로 주입 */
    private String apiKey;

    /** Gemini 모델 ID (예: gemini-2.5-flash, gemini-2.5-pro) */
    private String model = "gemini-2.5-flash";

    /** Gemini API base URL */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 5_000;

    /** 응답 대기 타임아웃 (ms) */
    private int readTimeoutMs = 60_000;

    /**
     * 응답 토큰 상한.
     * Gemini 2.5 Pro/Flash 는 thinking 토큰이 maxOutputTokens 안에 포함된다.
     * thinking 만으로 2~5K 소진될 수 있어 실제 출력 여유 확보 위해 크게 잡는다.
     */
    private int maxTokens = 16000;
}
