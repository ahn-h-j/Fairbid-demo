package com.cos.fairbid.ai.benchmark.golden;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * JSONL 포맷으로 된 Golden Dataset을 {@link GoldenCase} 리스트로 로드한다.
 *
 * <p>스펙상 필드는 snake_case 이므로 Jackson 의 {@link PropertyNamingStrategies#SNAKE_CASE}를
 * 적용해 record 필드(camelCase)와 매핑한다. 알 수 없는 필드는 무시한다.</p>
 *
 * <ul>
 *   <li>빈 줄과 {@code #} 주석 줄은 건너뛴다.</li>
 *   <li>파싱 실패 시 원본 줄 번호를 포함한 예외를 던진다(디버깅 편의).</li>
 * </ul>
 */
public final class GoldenCaseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private GoldenCaseLoader() {
        // 유틸리티 클래스
    }

    /**
     * 클래스패스 리소스에서 JSONL을 로드한다.
     *
     * @param classpath 예: {@code ai/golden/cases.jsonl}
     * @throws IllegalStateException 리소스 미존재 또는 파싱 실패
     */
    public static List<GoldenCase> loadFromClasspath(String classpath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(classpath)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Golden dataset not found on classpath: " + classpath);
            }
            return parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * InputStream(UTF-8)에서 JSONL을 파싱한다. 줄마다 하나의 {@link GoldenCase}.
     */
    public static List<GoldenCase> parse(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            int lineNo = 0;
            List<GoldenCase> cases = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                try {
                    cases.add(MAPPER.readValue(trimmed, GoldenCase.class));
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Failed to parse line " + lineNo + ": " + trimmed, e);
                }
            }
            return List.copyOf(cases);
        }
    }
}
