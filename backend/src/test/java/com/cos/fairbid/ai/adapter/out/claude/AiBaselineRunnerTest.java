package com.cos.fairbid.ai.adapter.out.claude;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.ConfidenceTrackingRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionLengthRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionQualityRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.HookRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PersonaRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PriceStructureRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PromptInjectionRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.ReformatRule;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.port.out.GuardrailFailurePort;
import com.cos.fairbid.ai.application.service.AiAssistService;
import com.cos.fairbid.ai.application.service.guardrail.InputGuardrailChain;
import com.cos.fairbid.ai.application.service.guardrail.OutputGuardrailChain;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.guardrail.GuardrailViolation;
import com.cos.fairbid.auction.domain.Category;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 베이스라인 측정 러너.
 *
 * <p>역할: v2(캐싱) / v3(하네스) 도입 전 현재 상태를 숫자로 박제한다.
 * 14개 회귀 케이스(cases.jsonl)를 실제 Claude API 로 호출해서 다음을 측정한다:
 * <ul>
 *   <li>케이스별 통과 여부 (mid 가 expectedPriceRangeKrw 범위 안인가)</li>
 *   <li>실패 분류 (예외 / 가격 역전 / 범위 이탈)</li>
 *   <li>호출당 latency (ClaudeApiAdapter 의 AI_METRIC 로그 캡처)</li>
 *   <li>토큰 사용량 / web_search 호출 수 / 추정 비용</li>
 * </ul>
 *
 * <p>운영 정책:
 * <ul>
 *   <li>비용 발생 작업이라 평소 빌드에서는 스킵 — {@code AI_BASELINE=true} 환경변수 + {@code ANTHROPIC_API_KEY}
 *       두 개가 모두 있을 때만 동작</li>
 *   <li>매 실행마다 {@code backend/build/ai-baseline/baseline-{timestamp}/} 디렉토리 생성</li>
 *   <li>per-case 결과는 results.jsonl, 집계는 summary.md 로 저장</li>
 *   <li>build 디렉토리는 gitignore 대상 — 결과를 영구 보관할 때는 {@code docs/spec/} 로 복사</li>
 * </ul>
 *
 * <p>Spring 컨텍스트를 띄우지 않는 이유:
 * test classpath 에 {@code FakeAiClient} 가 {@code @Primary} 로 등록돼 있어 SpringBootTest 로는
 * 실제 어댑터를 잡을 수 없다. 또 베이스라인 측정에는 DB/Redis 가 필요 없으므로 수동 와이어링이 가장 가볍다.
 */
@EnabledIfEnvironmentVariable(named = "AI_BASELINE", matches = "true")
class AiBaselineRunnerTest {

    private static final String CASES_RESOURCE = "ai/cases.jsonl";
    private static final Path OUTPUT_ROOT = Paths.get("build", "ai-baseline");

    // Claude Sonnet 4.5 가격 (2026-04 기준 가정)
    private static final double USD_PER_MTOK_INPUT = 3.0;
    private static final double USD_PER_MTOK_OUTPUT = 15.0;
    private static final double USD_PER_WEB_SEARCH = 0.01;
    private static final double KRW_PER_USD = 1330.0;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runBaseline() throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY 환경변수가 필요합니다. 예: ANTHROPIC_API_KEY=sk-ant-... AI_BASELINE=true ./gradlew test");
        }

        // 1. 어댑터 수동 와이어링 (Spring 컨텍스트 없이)
        AnthropicProperties props = new AnthropicProperties();
        props.setApiKey(apiKey);
        props.setWebSearchEnabled(false);

        ClaudePromptBuilder promptBuilder = new ClaudePromptBuilder(props);
        promptBuilder.loadSystemPrompt();

        ClaudeApiAdapter adapter = new ClaudeApiAdapter(props, promptBuilder, mapper);

        // 네이버 쇼핑 어댑터
        com.cos.fairbid.ai.adapter.out.naver.NaverSearchProperties naverProps =
                new com.cos.fairbid.ai.adapter.out.naver.NaverSearchProperties();
        naverProps.setClientId(System.getenv("NAVER_CLIENT_ID"));
        naverProps.setClientSecret(System.getenv("NAVER_CLIENT_SECRET"));
        com.cos.fairbid.ai.adapter.out.naver.NaverShoppingAdapter naverAdapter =
                new com.cos.fairbid.ai.adapter.out.naver.NaverShoppingAdapter(naverProps);

        // 입력 가드레일 (프롬프트 인젝션 차단)
        InputGuardrailChain inputChain = new InputGuardrailChain(List.of(new PromptInjectionRule()));

        // 출력 가드레일 (HARD: 가격/길이, SOFT: 설명 품질 / 페르소나 / 후크 / 리포맷 / confidence 추적)
        OutputGuardrailChain outputChain = new OutputGuardrailChain(List.of(
                new PriceStructureRule(),
                new DescriptionLengthRule(),
                new DescriptionQualityRule(),
                new PersonaRule(),
                new HookRule(),
                new ReformatRule(),
                new ConfidenceTrackingRule()
        ));

        // 실패 기록 — 러너에서는 DB 없이 로그만 남기는 no-op 구현
        GuardrailFailurePort failurePort = (violations, category, keyword, aiMidPrice, searchMedian, attemptCount) ->
                System.out.println("[ai-baseline]   guardrail failure recorded: " + violations.size() + " violations");

        // 시세 캐시 포트 — 러너에서는 항상 MISS (Redis 없음)
        com.cos.fairbid.ai.application.port.out.PriceCachePort priceCachePort =
                new com.cos.fairbid.ai.application.port.out.PriceCachePort() {
                    @Override
                    public java.util.Optional<com.cos.fairbid.ai.domain.AiAssistResult> find(
                            String category, String productKey, String grade) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void save(String category, String productKey, String grade,
                                     com.cos.fairbid.ai.domain.AiAssistResult result) {
                        // no-op
                    }
                };

        // AiAssistService 조립 (입력 가드레일 + 2단계 호출 + 출력 가드레일 + 캐시)
        AiAssistService service = new AiAssistService(
                adapter, naverAdapter, priceCachePort, inputChain, outputChain, failurePort);

        // 2. AI_METRIC 로그를 직접 캡처할 수 있게 ClaudeApiAdapter 로거에 ListAppender 부착
        Logger adapterLogger = (Logger) LoggerFactory.getLogger(ClaudeApiAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        adapterLogger.addAppender(appender);

        try {
            // 3. 케이스 로드
            // AI_BASELINE_LIMIT 환경변수로 첫 N 건만 돌릴 수 있음 (스모크 측정용).
            // 미지정 시 전체 케이스 실행.
            List<BaselineCase> cases = loadCases();
            String limitEnv = System.getenv("AI_BASELINE_LIMIT");
            if (limitEnv != null && !limitEnv.isBlank()) {
                int limit = Math.max(1, Math.min(Integer.parseInt(limitEnv), cases.size()));
                cases = cases.subList(0, limit);
            }
            System.out.println("[ai-baseline] loaded " + cases.size() + " cases");

            // 4. 실행
            // Anthropic Tier1 한도: input 30,000 tok/min. max_uses=1 가정 시 호출당 약 26K → 다음 호출은
            // 60초 윈도우가 굴러가도록 호출 사이 65초 대기. 마지막 케이스 후에는 sleep 안 함.
            // v2: 호출당 ~2.3K 토큰이라 rate limit 여유 충분. 최소 간격만 유지.
            final long sleepMillisBetweenCalls = 5_000L;
            List<BaselineResult> results = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                BaselineCase c = cases.get(i);
                int beforeLogCount = appender.list.size();
                System.out.println("[ai-baseline] (" + (i + 1) + "/" + cases.size() + ") " + c.id);
                BaselineResult r = runOne(service, c);
                Map<String, String> metric = findLatestMetric(appender.list, beforeLogCount);
                r.metric = metric;
                results.add(r);

                if (i < cases.size() - 1) {
                    System.out.println("[ai-baseline] sleeping " + (sleepMillisBetweenCalls / 1000) + "s for rate-limit window...");
                    try {
                        Thread.sleep(sleepMillisBetweenCalls);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("baseline run interrupted", ie);
                    }
                }
            }

            // 5. 결과 파일 작성
            Path runDir = createRunDir();
            writeResultsJsonl(runDir, results);
            writeSummary(runDir, results);
            System.out.println("[ai-baseline] results written to " + runDir.toAbsolutePath());
        } finally {
            adapterLogger.detachAppender(appender);
        }
    }

    /**
     * 단일 케이스 실행. AiAssistService 를 경유해 가드레일 루프를 포함한 전체 흐름을 탄다.
     * 예외도 결과로 흡수해서 다음 케이스가 계속 돌게 한다.
     */
    private BaselineResult runOne(AiAssistService service, BaselineCase c) {
        BaselineResult r = new BaselineResult();
        r.id = c.id;
        r.category = c.category;
        r.expectedMin = c.expectedPriceRangeKrw.min;
        r.expectedMax = c.expectedPriceRangeKrw.max;

        AiAssistCommand cmd = new AiAssistCommand(
                Category.valueOf(c.category),
                c.memo,
                c.imageUrls
        );

        try {
            AiAssistResult result = service.generate(cmd);
            r.outcome = "success";
            r.low = result.suggestedPrices().low();
            r.mid = result.suggestedPrices().mid();
            r.high = result.suggestedPrices().high();
            r.descriptionLength = result.generatedDescription() == null
                    ? 0
                    : result.generatedDescription().length();
            r.confidence = result.confidence();
            r.confidenceReason = result.confidenceReason();

            if (r.low == null || r.mid == null || r.high == null) {
                r.verdict = "FAIL_NULL_PRICE";
            } else if (r.low >= r.mid || r.mid >= r.high) {
                r.verdict = "FAIL_PRICE_INVERSION";
            } else if (r.mid < r.expectedMin || r.mid > r.expectedMax) {
                r.verdict = "FAIL_RANGE_MISS";
            } else {
                r.verdict = "PASS";
            }
        } catch (Exception e) {
            r.outcome = "error";
            r.errorType = e.getClass().getSimpleName();
            r.errorMessage = e.getMessage();
            r.verdict = "FAIL_EXCEPTION";
        }
        return r;
    }

    /**
     * cases.jsonl 을 한 줄씩 파싱. JSONL 포맷이라 ObjectMapper.readValues 대신 라인 단위 처리.
     */
    private List<BaselineCase> loadCases() throws IOException {
        ClassPathResource resource = new ClassPathResource(CASES_RESOURCE);
        List<BaselineCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                cases.add(mapper.readValue(trimmed, BaselineCase.class));
            }
        }
        return cases;
    }

    /**
     * appender 에 쌓인 로그 중 generate() 호출 이후 발생한 마지막 AI_METRIC 한 줄을 골라
     * key=value 페어로 파싱한다. 호출당 메트릭은 finally 에서 정확히 1번 찍히므로 마지막 한 줄만 본다.
     */
    private Map<String, String> findLatestMetric(List<ILoggingEvent> events, int fromIndex) {
        for (int i = events.size() - 1; i >= fromIndex; i--) {
            String msg = events.get(i).getFormattedMessage();
            if (msg != null && msg.startsWith("AI_METRIC ")) {
                return parseMetricLine(msg);
            }
        }
        return Map.of();
    }

    /**
     * "AI_METRIC k1=v1 k2=v2 ..." 형식을 LinkedHashMap 으로 분해.
     * v 에는 공백이 들어오지 않는다고 가정 (현 로깅 포맷 기준).
     */
    private Map<String, String> parseMetricLine(String line) {
        Map<String, String> out = new LinkedHashMap<>();
        String[] tokens = line.substring("AI_METRIC ".length()).split("\\s+");
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                out.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }
        return out;
    }

    private Path createRunDir() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = OUTPUT_ROOT.resolve("baseline-" + stamp);
        Files.createDirectories(dir);
        return dir;
    }

    private void writeResultsJsonl(Path runDir, List<BaselineResult> results) throws IOException {
        Path out = runDir.resolve("results.jsonl");
        try (var writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (BaselineResult r : results) {
                writer.write(mapper.writeValueAsString(r));
                writer.newLine();
            }
        }
    }

    /**
     * summary.md — 사람이 읽을 수 있는 집계.
     * 통과율, latency 분포, 토큰/비용, 실패 분류 카운트.
     */
    private void writeSummary(Path runDir, List<BaselineResult> results) throws IOException {
        int total = results.size();
        long pass = results.stream().filter(r -> "PASS".equals(r.verdict)).count();
        long failException = results.stream().filter(r -> "FAIL_EXCEPTION".equals(r.verdict)).count();
        long failInversion = results.stream().filter(r -> "FAIL_PRICE_INVERSION".equals(r.verdict)).count();
        long failRange = results.stream().filter(r -> "FAIL_RANGE_MISS".equals(r.verdict)).count();
        long failNull = results.stream().filter(r -> "FAIL_NULL_PRICE".equals(r.verdict)).count();

        List<Long> latencies = collectLong(results, "latency_ms");
        List<Long> inputTokens = collectLong(results, "input_tokens");
        List<Long> outputTokens = collectLong(results, "output_tokens");
        List<Long> webSearch = collectLong(results, "web_search_requests");

        long latencyP50 = percentile(latencies, 50);
        long latencyP95 = percentile(latencies, 95);
        long latencyMax = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        double latencyAvg = avg(latencies);

        double avgInput = avg(inputTokens);
        double avgOutput = avg(outputTokens);
        double avgWebSearch = avg(webSearch);

        // 호출당 평균 비용 (USD → KRW)
        double avgCostUsd =
                (avgInput / 1_000_000.0) * USD_PER_MTOK_INPUT
                        + (avgOutput / 1_000_000.0) * USD_PER_MTOK_OUTPUT
                        + avgWebSearch * USD_PER_WEB_SEARCH;
        double avgCostKrw = avgCostUsd * KRW_PER_USD;

        StringBuilder sb = new StringBuilder();
        sb.append("# AI Baseline Report\n\n");
        sb.append("- Generated: ").append(LocalDateTime.now()).append('\n');
        sb.append("- Cases: ").append(total).append('\n');
        sb.append("- Source: `backend/src/test/resources/ai/cases.jsonl`\n");
        sb.append("- Runner: `AiBaselineRunnerTest` (model=").append("default(claude-sonnet-4-5)").append(", web_search=on)\n\n");

        sb.append("## 통과율\n\n");
        sb.append("| 항목 | 건수 | 비율 |\n");
        sb.append("|------|------|------|\n");
        sb.append(row("PASS", pass, total));
        sb.append(row("FAIL_EXCEPTION", failException, total));
        sb.append(row("FAIL_NULL_PRICE", failNull, total));
        sb.append(row("FAIL_PRICE_INVERSION", failInversion, total));
        sb.append(row("FAIL_RANGE_MISS", failRange, total));
        sb.append('\n');

        sb.append("## Latency (ms)\n\n");
        sb.append("| metric | value |\n");
        sb.append("|---|---|\n");
        sb.append("| samples | ").append(latencies.size()).append(" |\n");
        sb.append("| avg | ").append(String.format(Locale.US, "%.0f", latencyAvg)).append(" |\n");
        sb.append("| p50 | ").append(latencyP50).append(" |\n");
        sb.append("| p95 | ").append(latencyP95).append(" |\n");
        sb.append("| max | ").append(latencyMax).append(" |\n\n");

        sb.append("## Tokens & Cost (호출당 평균)\n\n");
        sb.append("| metric | value |\n");
        sb.append("|---|---|\n");
        sb.append("| input_tokens | ").append(String.format(Locale.US, "%.0f", avgInput)).append(" |\n");
        sb.append("| output_tokens | ").append(String.format(Locale.US, "%.0f", avgOutput)).append(" |\n");
        sb.append("| web_search_requests | ").append(String.format(Locale.US, "%.2f", avgWebSearch)).append(" |\n");
        sb.append("| est. cost (USD) | $").append(String.format(Locale.US, "%.4f", avgCostUsd)).append(" |\n");
        sb.append("| est. cost (KRW) | ").append(String.format(Locale.US, "%.1f", avgCostKrw)).append("원 |\n\n");

        sb.append("## Per-case 결과\n\n");
        sb.append("| id | category | verdict | mid | expected | latency_ms | error |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (BaselineResult r : results) {
            sb.append("| ").append(r.id)
                    .append(" | ").append(r.category)
                    .append(" | ").append(r.verdict)
                    .append(" | ").append(r.mid == null ? "-" : r.mid.toString())
                    .append(" | ").append(r.expectedMin).append("~").append(r.expectedMax)
                    .append(" | ").append(metricOf(r, "latency_ms"))
                    .append(" | ").append(r.errorType == null ? "-" : r.errorType)
                    .append(" |\n");
        }

        Files.writeString(runDir.resolve("summary.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    private String row(String label, long count, long total) {
        double rate = total == 0 ? 0 : (count * 100.0 / total);
        return "| " + label + " | " + count + "/" + total
                + " | " + String.format(Locale.US, "%.1f%%", rate) + " |\n";
    }

    private String metricOf(BaselineResult r, String key) {
        if (r.metric == null) {
            return "-";
        }
        return r.metric.getOrDefault(key, "-");
    }

    private List<Long> collectLong(List<BaselineResult> results, String key) {
        List<Long> out = new ArrayList<>();
        for (BaselineResult r : results) {
            if (r.metric == null) {
                continue;
            }
            String v = r.metric.get(key);
            if (v == null || v.equals("-")) {
                continue;
            }
            try {
                out.add(Long.parseLong(v));
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private double avg(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (Long v : values) {
            sum += v;
        }
        return (double) sum / values.size();
    }

    // ---------- DTO ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BaselineCase {
        public String id;
        public String category;
        public List<String> imageUrls;
        public String memo;
        public ExpectedRange expectedPriceRangeKrw;
        public String notes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExpectedRange {
        public long min;
        public long max;
    }

    /**
     * 직렬화될 결과. 필드를 public 으로 둬 ObjectMapper 가 그대로 JSONL 로 쓰게 한다.
     */
    public static class BaselineResult {
        public String id;
        public String category;
        public long expectedMin;
        public long expectedMax;
        public String verdict;          // PASS / FAIL_*
        public String outcome;          // success / error
        public Long low;
        public Long mid;
        public Long high;
        public Integer descriptionLength;
        public String confidence;          // high / low
        public String confidenceReason;    // low 일 때의 불확실 사유
        public String errorType;
        public String errorMessage;
        public Map<String, String> metric;
    }
}
