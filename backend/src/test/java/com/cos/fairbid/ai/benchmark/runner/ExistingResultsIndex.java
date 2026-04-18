package com.cos.fairbid.ai.benchmark.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 이미 기록된 raw-results.jsonl을 스캔해 {@code caseId#runIdx} 키 집합을 반환한다.
 *
 * <p>중간 장애 후 재실행 시 완료된 (케이스, 실행 인덱스) 쌍을 건너뛰어 재개성을 제공한다.
 * 파일이 없으면 빈 집합.</p>
 *
 * <p>파싱 불가능한 줄은 조용히 무시한다(부분 쓰기 등). 이 경우 해당 run은 다시 실행된다.</p>
 */
public final class ExistingResultsIndex {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExistingResultsIndex() {
    }

    /**
     * @param jsonlFile 과거 결과 파일 경로
     * @return 이미 완료된 {@code "{caseId}#{runIdx}"} 키 집합
     */
    public static Set<String> scan(Path jsonlFile) {
        if (!Files.exists(jsonlFile)) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        try (Stream<String> lines = Files.lines(jsonlFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                try {
                    JsonNode node = MAPPER.readTree(line);
                    JsonNode caseIdNode = node.get("caseId");
                    JsonNode runIdxNode = node.get("runIdx");
                    if (caseIdNode == null || runIdxNode == null) {
                        return;
                    }
                    keys.add(caseIdNode.asText() + "#" + runIdxNode.asInt());
                } catch (IOException ignored) {
                    // 부분 기록된 줄 — 재실행으로 보정
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(keys);
    }
}
