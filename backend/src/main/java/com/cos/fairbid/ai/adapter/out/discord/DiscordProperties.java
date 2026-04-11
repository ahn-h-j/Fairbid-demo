package com.cos.fairbid.ai.adapter.out.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Discord Webhook 설정 (AI 어시스턴트 가드레일 리포트 전용).
 *
 * 환경변수 DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL 로 주입.
 * 저장소에 URL 하드코딩 금지.
 *
 * 다른 Discord 채널 웹훅과 식별되도록 `ai-assist-soft` 로 네임스페이스 분리.
 */
@Component
@ConfigurationProperties(prefix = "discord.ai-assist-soft")
@Getter
@Setter
public class DiscordProperties {

    /** Discord 채널 Webhook URL (없으면 리포트 전송 비활성화) */
    private String webhookUrl;

    /** 연결 타임아웃 (ms) */
    private int connectTimeoutMs = 3000;

    /** 읽기 타임아웃 (ms) */
    private int readTimeoutMs = 5000;

    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
