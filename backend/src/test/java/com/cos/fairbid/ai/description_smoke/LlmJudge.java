package com.cos.fairbid.ai.description_smoke;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.cos.fairbid.ai.benchmark.golden.GoldenCase;

/**
 * Claude Opus judge — 설명 품질 절대/쌍비교 채점.
 *
 * <p>SPEC §19 옵션 B 스모크 게이트 정의:</p>
 * <ul>
 *   <li>생성자(Claude Sonnet / Gemini Pro) 와 다른 모델 라인 사용 → self-preference 편향 완화</li>
 *   <li>5기준: hook / no_spec_dump / hidden_value / persona_clarity / no_reformat</li>
 *   <li>절대 모드: 1~5 점수 + reason</li>
 *   <li>쌍비교 모드: A/B/TIE</li>
 * </ul>
 *
 * <p>환경변수:</p>
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY} — 필수</li>
 *   <li>{@code ANTHROPIC_JUDGE_MODEL} — 필수. pilot 5건으로 한국어 채점 품질 선검증한 모델 ID (예: {@code claude-opus-4-5-20250930})</li>
 * </ul>
 */
public final class LlmJudge {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    /** 절대 채점 시스템 프롬프트. 길이는 평가 대상에서 제외. */
    private static final String SYSTEM_ABSOLUTE = """
            너는 중고 거래 플랫폼의 마케팅 카피 에디터다.
            제공된 상품 설명을 5개 기준으로 평가하라.
            각 점수는 1(매우 나쁨) ~ 5(매우 좋음) 정수이며 근거 문장 1개가 필수.
            길이는 평가 대상이 아니다.

            기준:
            1. hook — 첫 줄이 호기심/설득을 유발하는가
            2. no_spec_dump — 사양 나열 없이 가치 중심인가
            3. hidden_value — 구매자가 모를 수 있는 장점을 짚었는가
            4. persona_clarity — 누가 이 상품을 사야 하는지 명확한가
            5. no_reformat — memo 재배열 이상의 창작이 있는가

            출력은 JSON 오브젝트 한 개만 내라. 마크다운 펜스, 해설, 주석 금지.
            형식:
            {
              "hook": { "score": 1~5, "reason": "..." },
              "no_spec_dump": { "score": 1~5, "reason": "..." },
              "hidden_value": { "score": 1~5, "reason": "..." },
              "persona_clarity": { "score": 1~5, "reason": "..." },
              "no_reformat": { "score": 1~5, "reason": "..." }
            }
            """;

    /** 쌍비교 시스템 프롬프트. A/B 는 랜덤 순서 (caller 가 결정). */
    private static final String SYSTEM_PAIRWISE = """
            너는 중고 거래 플랫폼의 마케팅 카피 에디터다.
            같은 상품에 대한 두 설명 A, B 를 5개 기준으로 비교해 승자를 고르라.
            길이는 평가 대상이 아니다. 박빙이면 "TIE".

            기준:
            1. hook — 첫 줄이 호기심/설득을 유발하는가
            2. no_spec_dump — 사양 나열 없이 가치 중심인가
            3. hidden_value — 구매자가 모를 수 있는 장점을 짚었는가
            4. persona_clarity — 누가 이 상품을 사야 하는지 명확한가
            5. no_reformat — memo 재배열 이상의 창작이 있는가

            출력은 JSON 오브젝트 한 개만 내라. 마크다운 펜스, 해설, 주석 금지.
            형식:
            {
              "hook": "A" | "B" | "TIE",
              "no_spec_dump": "A" | "B" | "TIE",
              "hidden_value": "A" | "B" | "TIE",
              "persona_clarity": "A" | "B" | "TIE",
              "no_reformat": "A" | "B" | "TIE"
            }
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public LlmJudge(ObjectMapper objectMapper) {
        this(objectMapper, requireEnv("ANTHROPIC_API_KEY"), requireEnv("ANTHROPIC_JUDGE_MODEL"));
    }

    public LlmJudge(ObjectMapper objectMapper, String apiKey, String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    public AbsoluteScores absolute(GoldenCase caseInfo, String description) {
        String userContent = "상품: " + caseInfo.category() + " / " + caseInfo.id()
                + "\n설명:\n---\n" + description + "\n---";
        JsonNode json = call(SYSTEM_ABSOLUTE, userContent);
        Map<String, ScoreWithReason> scores = new java.util.LinkedHashMap<>();
        for (String key : List.of("hook", "no_spec_dump", "hidden_value", "persona_clarity", "no_reformat")) {
            JsonNode entry = json.get(key);
            int score = entry != null && entry.has("score") ? entry.get("score").asInt(0) : 0;
            String reason = entry != null && entry.has("reason") ? entry.get("reason").asText("") : "";
            scores.put(key, new ScoreWithReason(score, reason));
        }
        int total = scores.values().stream().mapToInt(ScoreWithReason::score).sum();
        return new AbsoluteScores(scores, total);
    }

    public PairwiseChoices pairwise(GoldenCase caseInfo, String descA, String descB) {
        String userContent = "상품: " + caseInfo.category() + " / " + caseInfo.id()
                + "\n\n설명 A:\n---\n" + descA + "\n---"
                + "\n\n설명 B:\n---\n" + descB + "\n---";
        JsonNode json = call(SYSTEM_PAIRWISE, userContent);
        Map<String, String> choices = new java.util.LinkedHashMap<>();
        for (String key : List.of("hook", "no_spec_dump", "hidden_value", "persona_clarity", "no_reformat")) {
            JsonNode entry = json.get(key);
            choices.put(key, entry == null ? "TIE" : entry.asText("TIE"));
        }
        return new PairwiseChoices(choices);
    }

    // ── API 호출 ──

    private JsonNode call(String system, String userContent) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", system,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", userContent
                ))
        );
        try {
            MessagesResponse response = restClient.post()
                    .uri(MESSAGES_PATH)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException(
                                "Judge API error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .body(MessagesResponse.class);

            if (response == null || response.content == null || response.content.isEmpty()) {
                throw new IllegalStateException("Judge returned empty content");
            }
            String raw = response.content.get(0).text;
            return parseJsonOrRetry(raw);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            throw new IllegalStateException(
                    "Judge API call failed: " + status + " (model=" + model + "). Body: "
                            + e.getResponseBodyAsString(), e);
        }
    }

    /** Judge 가 텍스트에 노이즈를 섞으면 첫 {..} 블록만 추출 후 재시도. */
    private JsonNode parseJsonOrRetry(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException firstFail) {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String candidate = raw.substring(start, end + 1);
                try {
                    return objectMapper.readTree(candidate);
                } catch (JsonProcessingException secondFail) {
                    // fallthrough
                }
            }
            throw new IllegalStateException(
                    "Judge response was not valid JSON. Raw: " + raw, firstFail);
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " env is required for LlmJudge");
        }
        return value;
    }

    // ── 결과 타입 ──

    public record ScoreWithReason(int score, String reason) {
    }

    public record AbsoluteScores(Map<String, ScoreWithReason> scores, int total) {
    }

    public record PairwiseChoices(Map<String, String> choices) {
        /** Unknown 응답을 TIE 로 정규화한 뷰. */
        public String normalized(String criterion) {
            String v = choices.getOrDefault(criterion, "TIE");
            return (v.equals("A") || v.equals("B")) ? v : "TIE";
        }
    }

    // ── API 응답 DTO (HTTP 바인딩용) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MessagesResponse {
        public List<ContentBlock> content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ContentBlock {
        public String type;
        public String text;
    }

    /** HTTP 상태 코드는 throw 지점에서만 쓰고, 응답 본문 해석은 상위에서 한다. */
    @SuppressWarnings("unused")
    private static boolean isOk(HttpStatus status) {
        return status.is2xxSuccessful();
    }
}
