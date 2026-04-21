package com.cos.fairbid.ai.description_smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * SPEC §19 옵션 B 스모크 게이트용 10건 케이스 선정.
 *
 * <p>선정 원칙 (SPEC §19):</p>
 * <ul>
 *   <li>카테고리 6종 × 1~2건 (전 카테고리 커버)</li>
 *   <li>Gemini 이미지 거부 빈발 케이스 제외 — 해당 케이스 id 블랙리스트</li>
 *   <li>Claude 설명이 벤치에서 다양한 점수 분포를 보인 케이스 우선 (수동 지정)</li>
 * </ul>
 *
 * <p>초기 선정은 {@link #DEFAULT_SELECTION} 고정 리스트로 제공. 측정 후 재조정.</p>
 */
public final class SmokeCaseSelector {

    /** 스모크 10건. 카테고리별 1~2건, 고가/저가/중고 등급 섞어 분포 다양화. */
    public static final List<String> DEFAULT_SELECTION = List.of(
            "iphone-15-pro-b",
            "airpods-pro-2-b",
            "nike-air-force-1-b",
            "chanel-classic-medium-a",
            "ikea-billy-bookcase-b",
            "dyson-v15-detect-b",
            "basketball-b",
            "taylormade-stealth2-driver-b",
            "polaroid-sx-70-b",
            "la-mer-creme-60ml-a"
    );

    private SmokeCaseSelector() {
    }

    /**
     * 전체 Golden 리스트에서 {@link #DEFAULT_SELECTION} 에 해당하는 케이스만 뽑는다.
     * 찾지 못한 id 는 예외로 알린다 (오탈자 / 데이터셋 변경 감지).
     */
    public static List<GoldenCase> select(List<GoldenCase> allCases) {
        return select(allCases, DEFAULT_SELECTION);
    }

    public static List<GoldenCase> select(List<GoldenCase> allCases, List<String> wantedIds) {
        Map<String, GoldenCase> byId = new LinkedHashMap<>();
        for (GoldenCase c : allCases) {
            byId.put(c.id(), c);
        }
        List<GoldenCase> result = new ArrayList<>(wantedIds.size());
        List<String> missing = new ArrayList<>();
        for (String id : wantedIds) {
            GoldenCase c = byId.get(id);
            if (c == null) {
                missing.add(id);
            } else {
                result.add(c);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Smoke selection ids not found in golden dataset: " + missing);
        }
        return List.copyOf(result);
    }
}
