package com.cos.fairbid.ai.adapter.out.claude;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Anthropic Claude API 설정 프로퍼티.
 * application.yml 의 ai.anthropic.* 항목을 바인딩한다.
 *
 * ai.provider=claude (미설정 시 기본값) 일 때만 빈 등록.
 */
@Getter
@Setter
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude", matchIfMissing = true)
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

    /**
     * web_search 화이트리스트 도메인. 비어있으면 무제한(전체 웹).
     * 도메인을 좁히면 검색 결과 페이지의 토큰량을 줄이고 시세 신뢰도가 높은 출처만 사용한다.
     */
    private List<String> webSearchAllowedDomains = Collections.emptyList();
}
