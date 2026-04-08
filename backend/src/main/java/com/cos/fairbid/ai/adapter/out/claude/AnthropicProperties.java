package com.cos.fairbid.ai.adapter.out.claude;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Anthropic Claude API 설정 프로퍼티.
 * application.yml 의 ai.anthropic.* 항목을 바인딩한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.anthropic")
public class AnthropicProperties {

    /** Anthropic API Key (sk-ant-...) — 환경변수 ANTHROPIC_API_KEY 로 주입 권장 */
    private String apiKey;

    /** Claude 모델 ID */
    private String model = "claude-sonnet-4-5";

    /** Anthropic API base URL */
    private String baseUrl = "https://api.anthropic.com";

    /** Anthropic API 버전 헤더 */
    private String anthropicVersion = "2023-06-01";

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 5_000;

    /** 응답 대기 타임아웃 (ms) — 웹 서치 사용 시 응답이 더 길어지므로 60초 기본 */
    private int readTimeoutMs = 60_000;

    /** 응답 토큰 상한 */
    private int maxTokens = 2000;

    /** Anthropic 내장 web_search 도구 활성화 여부 */
    private boolean webSearchEnabled = true;

    /** 한 호출당 web_search 최대 사용 횟수 (비용 가드) */
    private int webSearchMaxUses = 2;
}
