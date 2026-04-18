package com.cos.fairbid.ai.benchmark.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 스레드-세이프한 JSONL append writer.
 *
 * <p>여러 스레드가 동시에 {@link #append(RawResult)}를 호출해도 줄이 섞이지 않도록
 * 메서드 전체를 synchronized 로 보호한다. 대신 I/O 병목이 될 수 있는데,
 * 벤치마크 호출의 주 비용은 LLM API 지연(수초)이므로 fsync 수준에서 문제 없다.</p>
 *
 * <p>파일이 없으면 디렉토리 + 파일을 생성한다. 이미 있으면 이어쓰기(append).</p>
 */
public final class RawResultWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path file;

    public RawResultWriter(Path file) {
        this.file = file;
        ensureFile();
    }

    private void ensureFile() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 결과 한 줄을 append한다. I/O 실패는 호출자가 전파받도록 unchecked로 래핑.
     */
    public synchronized void append(RawResult result) {
        try {
            String line = MAPPER.writeValueAsString(result) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path file() {
        return file;
    }
}
