package com.cos.fairbid.ai.description_smoke;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.application.port.out.AiClientPort;
import com.cos.fairbid.ai.benchmark.golden.GoldenCase;
import com.cos.fairbid.ai.benchmark.golden.GoldenCaseLoader;
import com.cos.fairbid.ai.benchmark.runner.ModelAdapterFactory;
import com.cos.fairbid.ai.description_smoke.DescriptionQualityScorer.AutomatedMetrics;
import com.cos.fairbid.ai.description_smoke.LlmJudge.AbsoluteScores;
import com.cos.fairbid.ai.description_smoke.LlmJudge.PairwiseChoices;
import com.cos.fairbid.ai.domain.PricingResult;
import com.cos.fairbid.auction.domain.Category;

/**
 * SPEC §19 옵션 B 스모크 게이트 러너.
 *
 * <p>구 구조: 10건 케이스 × [Claude Sonnet, Gemini 2.5 Pro] 설명 생성 → 자동 지표 + judge 채점.</p>
 *
 * <p><b>상태 (2026-04-21 이후)</b>: SPEC §19 옵션 B 본 작업으로 {@code AiClientPort} 가 설명을 더는
 * 반환하지 않는다({@link PricingResult}). 기존 "Claude 설명 vs Gemini 설명" 쌍비교는 불가.
 * 재측정은 "프롬프트 보강 전 vs 후" 구조로 러너를 재설계해야 한다 — 별도 이슈로 분리 예정.</p>
 *
 * <p>컴파일 보존을 위해 구조는 유지하되 phase2Only 가 {@code PricingResult} 를 읽어 설명을 null 로 채운다.
 * 결과 report 는 역사적으로 의미 없으므로 실행을 피하고, 재설계 전까지 {@code DESCRIPTION_SMOKE_ENABLED}
 * 가드로 막힌 상태 그대로 둔다.</p>
 */
@EnabledIfEnvironmentVariable(named = "DESCRIPTION_SMOKE_ENABLED", matches = "true")
public class DescriptionSmokeRunnerTest {

    private static final String GOLDEN_CLASSPATH = "ai/golden/cases.jsonl";
    private static final String GENERATOR_CLAUDE = "claude";
    private static final String GENERATOR_GEMINI = "gemini-2.5-pro";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runSmokeGate() throws IOException {
        List<GoldenCase> allCases = GoldenCaseLoader.loadFromClasspath(GOLDEN_CLASSPATH);
        List<GoldenCase> selected = SmokeCaseSelector.select(allCases);
        // SMOKE_CASE_IDS 환경변수로 서브셋 실행 (쉼표 구분). 빈 값이면 전체.
        String limitRaw = System.getenv("SMOKE_CASE_IDS");
        if (limitRaw != null && !limitRaw.isBlank()) {
            java.util.Set<String> keep = java.util.Arrays.stream(limitRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
            selected = selected.stream().filter(c -> keep.contains(c.id())).toList();
        }

        ModelAdapterFactory factory = new ModelAdapterFactory(objectMapper);
        AiClientPort claude = factory.build(GENERATOR_CLAUDE).adapter();
        AiClientPort gemini = factory.build(GENERATOR_GEMINI).adapter();

        DescriptionQualityScorer scorer = new DescriptionQualityScorer();
        LlmJudge judge = new LlmJudge(objectMapper);
        Random rng = new Random(42L);

        Path outputDir = resolveOutputDir();
        Files.createDirectories(outputDir);
        Path rawPath = outputDir.resolve("raw-results.jsonl");

        List<CaseRecord> records = new ArrayList<>(selected.size());

        try (BufferedWriter writer = Files.newBufferedWriter(
                rawPath, StandardCharsets.UTF_8)) {

            for (GoldenCase c : selected) {
                System.out.println("[smoke] case=" + c.id());
                AiAssistCommand command = toCommand(c);

                // 1) Claude phase1: 이미지+메모 분석 → ProductAnalysis (공유 분석)
                // 2) Claude phase2: analysis + command 그대로 → 가격+설명 (설명만 사용)
                // 3) Gemini phase2: 동일 analysis 재사용, 이미지 뺀 command → 가격+설명 (설명만 사용)
                //    Gemini 는 phase1 을 돌리지 않으므로 이미지 관용도 이슈 회피 (옵션 B 모의)
                ProductAnalysis sharedAnalysis;
                try {
                    sharedAnalysis = claude.analyzeProduct(command);
                } catch (RuntimeException e) {
                    Generation failClaude = new Generation(
                            GENERATOR_CLAUDE, null, null, null, null,
                            e.getClass().getSimpleName(), e.getMessage());
                    CaseRecord rec = new CaseRecord(
                            c.id(), failClaude, null, null, null, "phase1_failed");
                    records.add(rec);
                    writer.write(objectMapper.writeValueAsString(rec));
                    writer.newLine();
                    continue;
                }

                AiAssistCommand descriptionCommand = new AiAssistCommand(
                        command.category(), command.memo(), List.of());

                Generation claudeGen = phase2Only(claude, command, sharedAnalysis, GENERATOR_CLAUDE);
                Generation geminiGen = phase2Only(gemini, descriptionCommand, sharedAnalysis, GENERATOR_GEMINI);

                if (claudeGen == null || geminiGen == null) {
                    CaseRecord rec = new CaseRecord(
                            c.id(), claudeGen, geminiGen, null, null, null);
                    records.add(rec);
                    writer.write(objectMapper.writeValueAsString(rec));
                    writer.newLine();
                    continue;
                }

                AutomatedMetrics claudeMetrics =
                        scorer.score(claudeGen.description, command);
                AutomatedMetrics geminiMetrics =
                        scorer.score(geminiGen.description, command);

                AbsoluteScores claudeAbsolute = judge.absolute(c, claudeGen.description);
                AbsoluteScores geminiAbsolute = judge.absolute(c, geminiGen.description);

                boolean claudeIsA = rng.nextBoolean();
                String descA = claudeIsA ? claudeGen.description : geminiGen.description;
                String descB = claudeIsA ? geminiGen.description : claudeGen.description;
                PairwiseChoices pairwiseRaw = judge.pairwise(c, descA, descB);
                Map<String, String> pairwiseByGenerator =
                        recoverPairwiseByGenerator(pairwiseRaw, claudeIsA);

                CaseRecord rec = new CaseRecord(
                        c.id(),
                        withMetrics(claudeGen, claudeMetrics, claudeAbsolute),
                        withMetrics(geminiGen, geminiMetrics, geminiAbsolute),
                        pairwiseByGenerator,
                        claudeIsA ? "A=claude, B=gemini" : "A=gemini, B=claude",
                        null);
                records.add(rec);
                writer.write(objectMapper.writeValueAsString(rec));
                writer.newLine();
            }
        }

        Path reportPath = outputDir.resolve("report.md");
        Files.writeString(
                reportPath,
                SmokeReportRenderer.render(records),
                StandardCharsets.UTF_8);

        System.out.println("[smoke] done. output=" + outputDir.toAbsolutePath());
    }

    private Path resolveOutputDir() {
        String override = System.getenv("BENCHMARK_OUTPUT_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());
        return Path.of("docs", "benchmark-results", "runs", "description-smoke-" + stamp);
    }

    private Generation phase2Only(
            AiClientPort adapter,
            AiAssistCommand command,
            ProductAnalysis sharedAnalysis,
            String label) {
        try {
            // SPEC §19 옵션 B 이후 PricingResult 에는 설명이 없다. 이 러너는 재설계 전까지 설명 null.
            PricingResult result = adapter.generatePricing(
                    command, sharedAnalysis, List.<PriceItem>of(), List.of());
            return new Generation(
                    label,
                    null,
                    result.confidence(),
                    sharedAnalysis.productName(),
                    sharedAnalysis.grade(),
                    "DEPRECATED_RUNNER",
                    "AiClientPort 가 설명을 더는 반환하지 않음. 러너 재설계 필요.");
        } catch (RuntimeException e) {
            return new Generation(
                    label, null, null, null, null,
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Generation withMetrics(
            Generation gen,
            AutomatedMetrics metrics,
            AbsoluteScores absolute) {
        return new Generation(
                gen.generator,
                gen.description,
                gen.confidence,
                gen.productName,
                gen.grade,
                gen.exceptionType,
                gen.exceptionMessage,
                metrics,
                absolute);
    }

    private Map<String, String> recoverPairwiseByGenerator(
            PairwiseChoices raw, boolean claudeIsA) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String criterion : List.of(
                "hook", "no_spec_dump", "hidden_value", "persona_clarity", "no_reformat")) {
            String choice = raw.normalized(criterion);
            if (choice.equals("TIE")) {
                out.put(criterion, "TIE");
            } else {
                boolean pickA = choice.equals("A");
                boolean winnerIsClaude = (pickA == claudeIsA);
                out.put(criterion, winnerIsClaude ? "claude" : "gemini");
            }
        }
        return out;
    }

    private AiAssistCommand toCommand(GoldenCase c) {
        Category category = c.category() == null
                ? null
                : Category.valueOf(c.category());
        boolean skipImages = "true".equalsIgnoreCase(System.getenv("BENCHMARK_SKIP_IMAGES"));
        List<String> images = (!skipImages && c.imageUrl() != null && !c.imageUrl().isBlank())
                ? List.of(c.imageUrl())
                : List.of();
        return new AiAssistCommand(category, c.memo(), images);
    }

    // ── 결과 타입 (JSONL 직렬화 대상) ──

    public record Generation(
            String generator,
            String description,
            String confidence,
            String productName,
            String grade,
            String exceptionType,
            String exceptionMessage,
            AutomatedMetrics automated,
            AbsoluteScores llmJudgeAbsolute
    ) {
        Generation(
                String generator,
                String description,
                String confidence,
                String productName,
                String grade,
                String exceptionType,
                String exceptionMessage) {
            this(generator, description, confidence, productName, grade,
                    exceptionType, exceptionMessage, null, null);
        }

        /** writer 용: NPE 방지하면서 JSON 에 자연스럽게 들어가도록 유지. */
        @Override
        public String toString() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public record CaseRecord(
            String caseId,
            Generation claude,
            Generation gemini,
            Map<String, String> pairwiseByGenerator,
            String pairwiseOrder,
            String error
    ) {
    }
}
