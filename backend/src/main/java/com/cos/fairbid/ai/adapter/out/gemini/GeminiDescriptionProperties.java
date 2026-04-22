package com.cos.fairbid.ai.adapter.out.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 설명 전용 Gemini 설정 프로퍼티 (SPEC §19 옵션 B).
 *
 * <p>{@link GeminiProperties} 와 분리한 이유:</p>
 * <ul>
 *   <li>가격용 벤치/프로바이더 스위치({@code ai.provider=gemini}) 와 무관하게, 설명 생성은
 *       <b>상시</b> Gemini 로 고정 (Claude 프로덕션 + Gemini 설명 조합)</li>
 *   <li>설명용은 Gemini 2.5 Pro 고정 (벤치 59% 실측, 스모크 Gemini 승률 58.1%)</li>
 * </ul>
 *
 * <p>application.yml 의 {@code ai.description.gemini.*} 를 바인딩한다.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.description.gemini")
public class GeminiDescriptionProperties {

    /** Google AI Studio API Key — 기본 환경변수 GEMINI_API_KEY 와 공유 */
    private String apiKey;

    /** 설명 생성용 모델. 기본 gemini-2.5-pro (스모크 기준 모델). */
    private String model = "gemini-2.5-pro";

    /** Gemini API base URL */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 5_000;

    /** 응답 대기 타임아웃 (ms) */
    private int readTimeoutMs = 60_000;

    /** 출력 토큰 상한. thinking 토큰 포함이라 여유 있게 확보. */
    private int maxTokens = 8_000;
}
