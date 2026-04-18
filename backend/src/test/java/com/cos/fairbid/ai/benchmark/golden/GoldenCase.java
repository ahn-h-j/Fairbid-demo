package com.cos.fairbid.ai.benchmark.golden;

import java.util.List;

/**
 * AI 벤치마크 러너가 소비하는 단일 Golden 케이스.
 *
 * <p>JSONL 한 줄이 하나의 레코드에 매핑된다. 필드는 벤치마크 스펙
 * ({@code docs/spec/ai-assist-spec.md} 및 태스크 정의) 참조.</p>
 *
 * <ul>
 *   <li>{@code id} — 재현/로그 추적용 고유 식별자 (slug).</li>
 *   <li>{@code category} — AuctionCategory 열거형 이름(문자열 그대로).</li>
 *   <li>{@code memo} — 판매자 입력 메모(개행 포함, 원문 유지).</li>
 *   <li>{@code imageUrl} — 상대/절대 경로. {@code null} 허용(이미지 없는 케이스).</li>
 *   <li>{@code expected} — 기대 가격 범위.</li>
 *   <li>{@code tags} — 분류 태그. 누락 시 빈 리스트로 정규화.</li>
 * </ul>
 */
public record GoldenCase(
        String id,
        String category,
        String memo,
        String imageUrl,
        Expected expected,
        List<String> tags
) {
    /**
     * 컴팩트 생성자에서 {@code tags} null → 빈 리스트로 정규화한다.
     * JSONL 역직렬화 후 호출자가 매번 null 검사하지 않도록 방어.
     */
    public GoldenCase {
        if (tags == null) {
            tags = List.of();
        }
    }
}
