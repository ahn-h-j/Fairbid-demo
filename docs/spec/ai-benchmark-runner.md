# AI Benchmark Runner 구현 문서

작성일: 2026-04-16
관련 문서: `ai-assist-spec.md` (AI 어시스트 전체 스펙)

---

## 0. TL;DR

- 기존 14건 회귀 셋의 통계적 신뢰도 부족 → Golden Dataset 30건 + 3중 지표(Strict / Score100 / IoU) + pass@k/pass^k 기반 벤치마크 러너 구축
- 러너는 `backend/src/test/java/com/cos/fairbid/ai/benchmark/` 하위에 JUnit 기반으로 구현 (프로덕션 코드 수정 없음, `ClaudePromptBuilder.loadSystemPrompt()` 가시성 변경만 1줄)
- 모델 병렬 + 케이스 3-병렬 + provider별 RPM 레이트 리미터. Redis 캐시는 `NoOpPriceCachePort` 수동 주입으로 우회
- 환경변수로 전체 제어: `BENCHMARK_MODELS`, `BENCHMARK_RUNS_PER_CASE`, `BENCHMARK_RPM_CLAUDE`, `BENCHMARK_SKIP_IMAGES` 등
- 결과는 `build/benchmark/{timestamp}/{model}/raw-results.jsonl` + 모델별 `report.md` + 전체 `comparison.md`
- JSONL append 방식으로 장애 중단 후 같은 `OUTPUT_DIR` 재지정해 재개 가능

---

## 1. 배경: 왜 평가 시스템을 재설계했나

### 1-1. 기존 평가의 한계

14건 회귀 셋으로 4개 모델을 3런씩 비교한 초기 리포트를 다시 보니 의심스러웠다.

**문제 1. 통계적 신뢰도 부족** — 14건 셋의 Wilson 95% 신뢰구간 폭이 너무 넓어 모델 간 차이를 통계적으로 증명할 수 없음. 구체 수치는 `ai-model-selection.md` 의 측정 기록 참조.

**문제 2. P/F 기준 불투명** — expected 범위 근거 없음. 폭도 케이스마다 1.75배~3.75배로 천차만별.

**문제 3. 경계값 이분법** — nike 75K → FAIL, 85K → PASS. 5천원 차이로 결정되는 게 시장 현실과 안 맞음.

**문제 4. 단일 지표(mid)만 검증** — API는 `{low, mid, high}` 3개를 주는데 평가는 mid만. 추천 범위의 합리성 누락.

### 1-2. 재설계 핵심

- **Golden Dataset 30건** (사람 수동 검증, 당근마켓 실거래 기반)
- **3중 지표**: Strict PASS(이분) + Score100(연속 점수) + IoU(범위 겹침)
- **pass@k / pass^k** (반복 실행 안정성)
- **Wilson 95% CI** (모든 비율 지표에)

---

## 2. Golden Dataset

### 2-1. 위치 / 파일

`backend/src/test/resources/ai/golden/cases.jsonl` — JSONL 포맷, 한 줄 = 한 케이스.

### 2-2. 스키마 (v2-lite)

```json
{
  "id": "iphone-15-pro-b",
  "category": "ELECTRONICS",
  "memo": "아이폰 15 프로 256기가 블루티타늄\n작년 말쯤 구매했고 사용감 있어요\n박스 케이블 다 있습니다",
  "image_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/IPhone_Pro.jpg/...",
  "expected": {
    "low": 780000,
    "high": 1000000,
    "tolerance_pct": 10,
    "source": "당근 2026-04 실측 10건"
  },
  "tags": []
}
```

**Jackson 파싱 설정**: `PropertyNamingStrategies.SNAKE_CASE` 적용. 레코드 필드는 camelCase(`imageUrl`, `tolerancePct`)지만 JSONL은 snake_case로 저장 가능.

### 2-3. 필드 처리 규칙

| 필드 | 처리 |
|---|---|
| `expected.mid` | 저장 안 함, `(low+high)/2` 로 계산 (`Expected.mid()`) |
| `tolerance_pct` | **Score100 도입 후 미사용** (구 Soft PASS 유산, 파싱은 호환 유지) |
| `tags` 누락 | 빈 리스트로 정규화 (`GoldenCase` 컴팩트 생성자) |
| `image_url` | HTTPS 공개 URL 권장. 상대 경로는 API 서버에서 거절됨 (§4-3) |

### 2-4. 카테고리 분포 (5×6=30)

```
ELECTRONICS  5건  — iPhone, MacBook, PS5, AirPods, Galaxy Buds3 Pro
FASHION      5건  — 에어포스, 눕시, 룰루레몬, G-Shock, 샤넬 클래식
HOME         5건  — 임스, 빌리 책장, 뉴마틱, 다이슨 V15, 발뮤다
SPORTS       5건  — 브롬튼, 농구공, 자이언트, 테일러메이드, 베이퍼플라이
HOBBY        5건  — 게임보이, 펜더, 폴라로이드, 포켓몬 박스, 레고 타이타닉
OTHER        5건  — 라메르, SK-II, 스탠리, 딥디크, 킨토
```

### 2-5. tags 분류

```
boundary_price       — 경계값 케이스 (nike)
vintage_premium      — 빈티지/한정판 감가 적음 (eames, brompton, fender, polaroid, game-boy, chanel)
discontinued         — 단종 모델 (fender, game-boy)
overseas_niche       — 국내 유통 제한 (numatic)
low_search_volume    — 매물 5건 미만 (numatic, eames, fender)
quantity_ambiguous   — 단품/세트 혼재 (pokemon, vaporfly)
brand_ambiguous      — 같은 브랜드 다른 등급 (fender vs squier)
high_value           — 100만원 이상 고가 (eames, chanel)
low_price            — 5만원 이하 (basketball, stanley, kinto, billy)
```

리포트의 `By Tag` 섹션에서 tag 기반 slice 분석 제공.

### 2-6. 데이터 수집 프로세스 (1건당 5~7분)

1. 당근마켓 → 판매완료 필터
2. 가격 10개 수집 (썸네일/제목으로 본체만 확인)
3. 상하위 1개씩 제외 → 중간 8개로 low/high 결정
4. 이상치 제거 (가품/부품/다른 모델)
5. `source` 에 "N건 (X건 제외)" 기록 (추적 가능)

**이상치 제거 사례**
- 샤넬 130/125만원 → 가품 의심 (정품 700만원대)
- 임스 180/240만원 → 레플리카 (허먼밀러 정품 800만원+)
- 게임보이 235/155만원 → 희귀 컬러/박스

### 2-7. memo 작성 원칙 — 유저 톤

카탈로그 톤이 아니라 실제 판매자가 쓸 법한 짧고 대충된 톤. 등급 표현(B급/양호) 제외 (AI가 memo에서 자체 판정하도록).

**Before** (카탈로그 톤): `에어포스 1 '07 로우 화이트 270mm / 2024년 봄 구매 / 양호 (2~3회 착용) / 정품 박스 포함`

**After** (유저 톤): `에어포스1 270 화이트 / 작년 봄에 샀고 몇번 안 신었어요 / 박스 있음`

---

## 3. 평가 지표

### 3-1. Strict PASS (이분)

```
condition: expected.low ≤ mid ≤ expected.high
score: 1.0 또는 0.0
```

구현: `VerdictScorer.strictPass(gc, mid)`.

### 3-2. Score100 (0~100 연속 점수)

**알고리즘**:
```
if (mid <= 0) return 0;
if (mid ∈ [low, high]) return 100;
distance = mid < low ? (low - mid) : (mid - high);
tolerance = (width > 0) ? 2.5 * width : 0.25 * max(1, |low|);  // width == 0 폴백
ratio = distance / tolerance;
return (ratio >= 1.0) ? 0 : 100 * (1 - ratio);
```

**예시** (nike: `low=80000, high=140000`, `width=60000, tolerance=150000`):

| mid | distance | Score |
|---|---:|---:|
| 100000 (한가운데) | 0 | 100 |
| 80000 / 140000 (경계) | 0 | 100 |
| 75000 | 5000 | 96.67 |
| 50000 | 30000 | 80.0 |
| 200000 | 60000 | 60.0 |
| 290000 (tolerance edge) | 150000 | 0 |
| 1000000 이상 | 초과 | 0 |
| 0 또는 음수 | — | 0 |

**왜 Soft PASS(0/0.5/1.0)에서 교체했나**
- Soft는 3단계라 "빗나간 정도" 반영 못 함 (75K와 10K가 둘 다 0.0)
- Score100은 거리 비례 연속값
- 리포트 가독성: "87.5 / 100" 이 "Soft PASS 92.7%" 보다 직관
- `tolerance_pct` 필드 의존 제거 → 스키마 단순

구현: `VerdictScorer.score100(gc, mid)`.

### 3-3. IoU (범위 겹침)

```
intersection = max(0, min(rec.high, exp.high) - max(rec.low, exp.low))
union        = max(rec.high, exp.high) - min(rec.low, exp.low)
iou          = intersection / union  (union=0이면 1.0)
```

`{low, mid, high}` 추천 범위 전체 평가. 단일 mid로는 보이지 않는 범위 합리성 체크.

**평균 집계 정책 — Strict PASS run만 포함**
Strict FAIL 케이스는 mid 가 범위 밖이라 대부분 IoU=0. 이를 포함해 평균을 내면 "추천 범위 품질"이 아닌 "Strict PASS rate 종속 지표"가 되어 의미 희석. 따라서 모델/카테고리/태그/케이스 전역에서 **IoU 평균은 Strict PASS 인 run 만** 집계한다. 해석은 "맞췄을 때 추천 범위가 얼마나 정답 범위와 겹치는가".

구현: `VerdictScorer.iou(gc, recLow, recHigh)` + `ReportAggregator.meanIouOnStrictPass`.

### 3-4. pass@k / pass^k (반복 실행)

각 케이스 n회 반복, 통과 횟수 c 기준:

```
pass@1 = c / n
pass@k = 1 - C(n-c, k) / C(n, k),  n-c<k면 1.0
pass^k = (c/n)^k
```

k=3 고정 (LLM eval 관례). 모든 케이스의 run 수가 3 이상일 때만 계산, 아니면 리포트에 "—".

구현: `PassAtK.passAt1 / passAtK / passPowerK`.

**해석**
| pass@1 | pass^3 | 진단 |
|---|---|---|
| 1.0 | 1.0 | 완벽 |
| 0.7 | 0.34 | 변동성 문제 (temperature/seed 조정) |
| 0.2 | 0.008 | 능력 부족 (프롬프트/모델 변경) |

### 3-5. Wilson 95% 신뢰구간

```
z = 1.96
denom  = 1 + z²/n
center = (p + z²/(2n)) / denom
margin = z × √(p(1-p)/n + z²/(4n²)) / denom
CI = [center - margin, center + margin]  (n=0이면 [0,1])
```

구현: `WilsonCI.compute(c, n) → Bounds`.

---

## 4. 러너 아키텍처

### 4-1. 패키지 구조

```
backend/src/test/java/com/cos/fairbid/ai/benchmark/
├── BenchmarkSettings.java              # env var 파싱
├── AiBenchmarkRunnerTest.java          # JUnit 엔트리 (@EnabledIfEnvVar)
├── golden/
│   ├── GoldenCase.java                 # 레코드
│   ├── Expected.java
│   └── GoldenCaseLoader.java           # JSONL snake_case 파서
├── score/
│   ├── VerdictScorer.java              # Strict / Score100 / IoU
│   ├── PassAtK.java                    # pass@k / pass^k / binomial
│   └── WilsonCI.java                   # Wilson 신뢰구간
├── runner/
│   ├── BenchmarkOrchestrator.java      # 모델 병렬 + 케이스 병렬 루프
│   ├── ModelExecutor.java              # 함수형 인터페이스
│   ├── DryRunModelExecutor.java        # mock executor
│   ├── RealModelExecutor.java          # AiAssistService 래퍼
│   ├── ModelAdapterFactory.java        # 모델명 → AiClientPort 구성
│   ├── NoOpPriceCachePort.java         # 캐시 우회
│   ├── PipelineRateLimiter.java        # provider별 RPM 슬롯 스케줄러
│   ├── RawResult.java                  # 1회 실행 기록
│   ├── RawResultWriter.java            # JSONL append (synchronized)
│   ├── ExistingResultsIndex.java       # 재개성 — 완료된 (caseId, runIdx) 스캔
│   └── ProgressLogger.java             # stdout [done/total] 로그
└── report/
    ├── ModelReport.java                # 집계 record 타입
    ├── ReportAggregator.java           # 집계 로직
    ├── MarkdownRenderer.java           # MD 렌더
    └── Reporter.java                   # JSONL 읽기 + 파일 쓰기
```

### 4-2. 환경변수 인터페이스

| 변수 | 용도 | 기본값 |
|---|---|---|
| `BENCHMARK_MODELS` | 쉼표 구분 모델 (claude, openai, gemini, claude-sonnet-4-5, gpt-4.1-mini 등) | **필수** |
| `BENCHMARK_RUNS_PER_CASE` | 케이스당 반복 수 | 5 |
| `BENCHMARK_CACHE_DISABLED` | Redis 시세 캐시 우회 (현재 구현은 NoOp 강제이므로 사실상 항상 true) | false |
| `BENCHMARK_DRY_RUN` | 실제 API 호출 없이 mock executor 로 러너 파이프라인만 검증 | false |
| `BENCHMARK_OUTPUT_DIR` | 결과 디렉토리. 같은 경로 재지정 시 JSONL append로 재개 | `build/benchmark/{yyyyMMdd-HHmmss}` |
| `BENCHMARK_CASES_PATH` | Golden JSONL 클래스패스 | `ai/golden/cases.jsonl` |
| `BENCHMARK_CASES_LIMIT` | 앞 N건만 실행 (스모크용) | 전체 |
| `BENCHMARK_SKIP_IMAGES` | `true`면 memo 단독 추론 (§4-7) | false |
| `BENCHMARK_RPM_CLAUDE` | Claude provider 최대 RPM. 0 = 무제한 | 0 |
| `BENCHMARK_RPM_OPENAI` | OpenAI RPM | 0 |
| `BENCHMARK_RPM_GEMINI` | Gemini RPM | 0 |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY` | 모델별 키. 해당 모델 포함 시 필수 | — |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 네이버 검색 (드라이런 아닌 경우 필수) | — |

### 4-3. 출력 구조

```
build/benchmark/{timestamp}/
├── claude/
│   ├── raw-results.jsonl       # 한 줄 = 1 run (성공/예외 모두)
│   └── report.md
├── openai/
│   ├── raw-results.jsonl
│   └── report.md
├── gemini/
│   ├── raw-results.jsonl
│   └── report.md
└── comparison.md               # 전체 비교표
```

### 4-4. 병렬성 정책

- **모델 간 병렬** — `ExecutorService` 로 모든 모델 동시 실행 (`BenchmarkOrchestrator.run`).
- **케이스 내 병렬** — 모델당 `CASE_PARALLELISM=3` 스레드 풀에서 3 케이스 동시. 같은 케이스 내 run은 순차.
- **총 동시 호출 상한** — 3 모델 × 3 케이스 = **최대 9 파이프라인 동시**.
- **러너 자체 재시도 없음** — 프로덕션 가드레일의 1회 HARD 재시도는 그대로. 429/네트워크 오류는 `RawResult.exceptionType`에 기록되고 다음 run으로 넘어감.

### 4-5. Rate Limiter — Provider 공유 슬롯

`PipelineRateLimiter`: provider 하나당 단일 인스턴스를 해당 provider의 모든 모델 executor가 공유. 파이프라인 시작 직전 `acquire()` 로 다음 슬롯 예약.

```
intervalMs = 60000 / maxPerMinute
acquire() {
    slot = max(now, nextAvailable)
    nextAvailable = slot + intervalMs
    sleep(slot - now) if positive
}
```

**왜 "매 호출 sleep"이 아니라 "슬롯 예약"인가** — API 호출이 자연스럽게 느리면(Claude 15s, Gemini 22s) 이미 분당 RPM 밑이라 sleep 불필요. 슬롯 기반은 자연 지연을 낭비하지 않음.

**왜 Claude만 기본 RPM 필요한가**:

| Provider | Tier 1 제약 | 병목 |
|---|---|---|
| Claude Sonnet 4.5 | 50 RPM, 30K ITPM(cache read 무과금), **8K OTPM** | OTPM 타이트 |
| OpenAI gpt-4.1-mini | 500 RPM, 200K TPM | 여유 |
| Gemini 2.5 Flash (paid) | 1000 RPM, 4M TPM | 여유 |

Claude Tier 1 의 OTPM 8K 가 실질 병목. 파이프라인당 output ~1K 토큰 × 분당 3 슬롯 × 2 phase = ~6K OTPM 수준으로 맞추려면 `BENCHMARK_RPM_CLAUDE=5` 권장.

### 4-6. 재개성 — JSONL append + ExistingResultsIndex

- `RawResultWriter.append()` 는 synchronized 로 단일 파일 append
- 러너 시작 시 `ExistingResultsIndex.scan(jsonlFile)` 이 기존 파일을 읽어 `{caseId}#{runIdx}` 키 집합 반환
- 이미 기록된 (case, run) 는 건너뜀 + `SKIP` 로그
- 중단 후 `BENCHMARK_OUTPUT_DIR` 를 같은 경로로 주면 남은 run만 실행

**한계**: 예외(EXCEPTION)도 "완료"로 간주되어 재실행에서 건너뜀. rate-limit 428 등으로 실패한 run을 재시도하려면 해당 줄을 수동 삭제 후 재실행.

### 4-7. 이미지 처리 — `BENCHMARK_SKIP_IMAGES`

Golden dataset 초기 버전은 `image_url: "./images/..."` 상대 경로였으나 Anthropic/OpenAI/Gemini 모두 **HTTPS URL만 허용** ("Only HTTPS URLs are supported").

두 가지 대응:
1. **이미지 포함** (default): 구현된 `image_url`은 위키피디아 공개 URL로 매핑 (`backend/scripts/replace_image_urls.py` 로 기존 cases.jsonl 의 URL을 신규 30건에 매칭 재사용)
2. **이미지 스킵** (`BENCHMARK_SKIP_IMAGES=true`): `RealModelExecutor.toCommand` 에서 `imageUrls` 를 빈 리스트로 전달. memo 단독 추론

### 4-8. Spring 없는 수동 와이어링

**왜 `@SpringBootTest` 를 쓰지 않는가**
- 각 어댑터(Claude/OpenAI/Gemini)가 `@ConditionalOnProperty("ai.provider", havingValue="...")` 로 가드되어 단일 Spring 컨텍스트에선 **한 provider만 활성화**됨
- 벤치마크는 3 provider 를 동시에 쓰고 싶음

**대응** — `AiBenchmarkRunnerTest` 가 모든 빈을 수동 구성:
- `ModelAdapterFactory` 가 provider별 `AiClientPort` 를 env var API 키로 new 해서 반환
- `InputGuardrailChain` / `OutputGuardrailChain` 은 기존 Rule 구현체들을 `new` 로 묶어서 주입
- `PriceCachePort` 는 `NoOpPriceCachePort` 직접 주입 (Redis 불필요)
- `PriceSearchPort` 는 실제 `NaverShoppingAdapter` 를 네이버 크레덴셜로 구성
- `GuardrailFailurePort` 는 no-op 람다 (DB 없이 raw-results.jsonl 로 충분)
- 각 모델마다 `new AiAssistService(...)` 로 독립 인스턴스 조립 → `RealModelExecutor` 로 래핑

**프로덕션 변경 범위** — `ClaudePromptBuilder.loadSystemPrompt()` 의 가시성을 package-private → public 으로 승격 (1줄). OpenAI/Gemini 는 이미 public. 로직 변화 없음.

### 4-9. Verdict 간소화

`RawResult.verdict()` 반환값:
- `EXCEPTION` — `exceptionType != null`
- `PASS` — `strictPass == 1.0`
- `FAIL` — 그 외

Soft PASS 제거에 맞춰 `SOFT` verdict 도 제거. Score100은 연속값이라 verdict 범주와 직교.

---

## 5. 리포트

### 5-1. 모델별 `report.md`

```markdown
# AI Benchmark Report — {model}

- Cases: N, Total runs: N×R, Exceptions: E

## Overall
| Metric | Value |
|---|---|
| Strict PASS rate | XX.X%  (95% CI XX.X% – XX.X%) |
| Mean Score | YY.Y / 100 |
| Mean IoU | 0.ZZZ |
| pass@1 | 0.XXX |
| pass@3 | 0.XXX |
| pass^3 | 0.XXX |

## By Category
| Bucket | Cases | Runs | Strict | Score | IoU |
| ELECTRONICS | 5 | 5R | XX.X% | YY.Y | 0.ZZZ |
| FASHION     | 5 | 5R | XX.X% | YY.Y | 0.ZZZ |
...

## By Tag
| Bucket | Cases | Runs | Strict | Score | IoU |
| boundary_price  | 1 | R   | XX.X% | YY.Y | 0.ZZZ |
| vintage_premium | 6 | 6R  | XX.X% | YY.Y | 0.ZZZ |
...

## Bottom 3 Cases
| Case | Category | Runs | Strict | Score | IoU |
| {caseId} | {CAT} | R |  XX.X% | YY.Y | 0.ZZZ |
| {caseId} | {CAT} | R |  XX.X% | YY.Y | 0.ZZZ |
| {caseId} | {CAT} | R |  XX.X% | YY.Y | 0.ZZZ |

## Exceptions
| Case | Run | Type | Message |
| {caseId} | r | AiServiceUnavailableException | 429 rate_limit |
| ... |
```

- **Bottom 3 정렬** — `meanScore100` 오름차순, 동률이면 `strictPassRate` 오름차순
- **Mean Score 포맷** — 표엔 `YY.Y`, Overall 섹션엔 `YY.Y / 100`

### 5-2. `comparison.md`

```markdown
# AI Benchmark — Model Comparison

| Model | Cases | Runs | Strict | Score | IoU | pass@1 | pass@3 | pass^3 | Exceptions |
| claude | 30 | 30R | XX.X% | YY.Y | 0.ZZZ | 0.XXX | 0.XXX | 0.XXX | E |
| openai | 30 | 30R | XX.X% | YY.Y | 0.ZZZ | 0.XXX | 0.XXX | 0.XXX | E |
| gemini | 30 | 30R | XX.X% | YY.Y | 0.ZZZ | 0.XXX | 0.XXX | 0.XXX | E |

_95% Wilson CI per model:_
- claude: XX.X% – XX.X%
- openai: XX.X% – XX.X%
- gemini: XX.X% – XX.X%
```

---

## 6. 사용법

### 6-1. 드라이런 (API 키 불필요)

러너 파이프라인 구조 검증용. `DryRunModelExecutor` 가 expected.mid 그대로 반환.

```bash
BENCHMARK_MODELS=claude,openai \
BENCHMARK_DRY_RUN=true \
BENCHMARK_RUNS_PER_CASE=3 \
BENCHMARK_OUTPUT_DIR=build/bench-dryrun \
./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
```

### 6-2. 스모크 (1 케이스 × 1 run × 3 모델)

실 API로 3 모델 다 도는지 검증. 소요 ~1분, 비용 ~100원.

```bash
set -a; source .env; set +a
BENCHMARK_MODELS=claude,openai,gemini \
BENCHMARK_RUNS_PER_CASE=1 \
BENCHMARK_CASES_LIMIT=1 \
BENCHMARK_CACHE_DISABLED=true \
BENCHMARK_OUTPUT_DIR=build/bench-smoke \
./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
```

### 6-3. 본 벤치마크 (30 × 10 runs × 3 모델 = 900 pipelines)

```bash
set -a; source .env; set +a
BENCHMARK_MODELS=claude,openai,gemini \
BENCHMARK_RUNS_PER_CASE=10 \
BENCHMARK_CACHE_DISABLED=true \
BENCHMARK_RPM_CLAUDE=5 \
BENCHMARK_OUTPUT_DIR=build/bench-10runs \
./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
```

예상 소요 — Claude RPM=5 기준 ~60분 (Claude 가 전체 시간 결정, OpenAI/Gemini 는 Claude 보다 빨리 끝남). 예상 비용 ~1만 2천 원.

### 6-4. 재개

실행 중단 후 같은 `BENCHMARK_OUTPUT_DIR` 를 지정하면 남은 run만 실행:

```bash
BENCHMARK_OUTPUT_DIR=build/bench-10runs \
[... 나머지 동일 ...] \
./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
```

### 6-5. 단일 모델

`BENCHMARK_MODELS=claude` 로 특정 모델만. 구체 모델 ID도 가능 (`claude-sonnet-4-5`, `gpt-4.1-mini`, `gemini-2.5-pro`).

---

## 7. 구현 결정 기록

### 7-1. Soft PASS → Score100

**기각**: Soft PASS (0/0.5/1.0 이산값)
**채택**: Score100 (0~100 연속값)

**이유**
- Soft 3단계로는 "얼마나 빗나갔는지" 구분 불가 — nike 75K(5K 빗나감)과 10K(70K 빗나감)이 둘 다 0.0
- Score100 은 거리 비례 연속, 선형 감쇠
- 리포트 가독성: "87.5점" > "Soft PASS rate 92.7%"
- 학술적으로 Winkler Score(Prediction Interval 평가)를 100점 만점으로 정규화한 변형
- `tolerance_pct` 의존 제거 → 스키마 단순

**부작용** — 기존 14건 측정과 직접 수치 비교 불가.

### 7-2. Spring `@SpringBootTest` vs 수동 와이어링

**채택**: 수동 와이어링 (AiBenchmarkRunnerTest 가 JUnit 테스트이지만 Spring 컨텍스트 안 띄움)

**이유**
- `@ConditionalOnProperty("ai.provider")` 가 provider 한 종류만 활성화
- 3 provider 동시 벤치마크 목적과 충돌
- 테스트 클래스패스에 `FakeAiClient` 가 `@Primary` 로 등록돼 있어 `@SpringBootTest` 로는 실어댑터 잡기 어려움
- 수동 와이어링은 가볍고 의존성 명시적 — 기존 `AiBaselineRunnerTest` 와 동일 패턴

### 7-3. 모델 간 병렬 vs 순차

**채택**: 병렬 (`ExecutorService` at 모델 레벨)

**이유**
- Provider별 rate limiter가 공유 인스턴스라 병렬 실행이 상한을 깨지 않음
- 순차 실행 시 전체 시간 ≈ Σ(모델별 시간). 병렬은 ≈ max(모델별 시간)
- 10 runs 기준 순차 ~105분 → 병렬 ~60분 (40% 단축)

### 7-4. 이미지 URL 처리

**상황** — Golden dataset 초기 상대경로는 API 서버에서 거절

**채택**: 기존 14건 cases.jsonl 의 위키피디아 URL을 신규 30건에 매핑
- 자동 매칭 (ID 토큰 Jaccard 유사도) 27/30 성공
- 수동 매핑 3건 추가 (casio-g-shock ← gshock-5600 등)
- 스크립트: `backend/scripts/replace_image_urls.py`

**이유 — 왜 상대 경로/로컬 base64 를 쓰지 않는가**
- 프로덕션 어댑터는 HTTPS URL 전제로 설계. base64 지원 추가는 어댑터 로직 변경 필요 → 벤치마크 스코프 밖
- 이미지 자체가 벤치마크 목적이 아니면 `BENCHMARK_SKIP_IMAGES=true` 로 우회 가능

### 7-5. Rate limiter 설계 — 매 호출 sleep vs 슬롯 예약

**기각**: 매 호출 완료 후 고정 시간 sleep
**채택**: provider 공유 슬롯 스케줄러 (`PipelineRateLimiter`)

**이유**
- 매 호출 sleep 은 자연 지연(Claude 15s, Gemini 22s)이 이미 상한 밑이어도 불필요하게 대기
- 슬롯 기반은 "다음 호출 시작 허용 시각" 을 예약 → 자연 지연이 길면 대기 0
- 3 parallel 스레드가 공유 limiter 통해 slot 경합 → 전체 RPM 이 자연스럽게 상한 수렴

### 7-6. 기존 14건 회귀 셋과 `AiBaselineRunnerTest` 제거

**채택**: 기존 러너(627줄) 삭제, 기존 cases.jsonl(14건) 보존

**이유**
- 신규 러너가 기능적으로 상위호환 (스코어/pass@k/리포트 추가)
- 기존 cases.jsonl 은 이미지 URL 소스로 계속 필요 (§4-7)

---

## 8. 제한사항 / 알려진 이슈

- **이미지 base64 미지원** — API 서버가 HTTPS URL만 받음. 로컬 파일 기반 케이스는 `BENCHMARK_SKIP_IMAGES=true` 거나 공개 URL로 호스팅 필요
- **예외 run 재시도 불가** — `ExistingResultsIndex` 가 exception 을 "완료"로 취급. rate-limit 실패 재시도는 JSONL 수동 편집 후 재실행
- **TestContainers Docker 의존** — `FairBidApplicationTests`, Cucumber 테스트는 Docker 필요. 벤치마크 테스트 자체는 Docker 불필요하나 `./gradlew test` 전체 실행 시 이 테스트들이 Docker 없으면 실패 (벤치마크 무관)
- **프로덕션 어댑터 `@ConditionalOnProperty`** — 현재 수동 와이어링으로 우회하지만, 향후 어댑터 구조 바뀌면 팩토리 동기화 필요

---

## 9. 향후 작업

### 단기
1. 첫 정식 측정 결과 분석 → 모델 선정 의사결정 문서화
2. 약점 카테고리/태그 식별 → 프롬프트/룰 개선 사이클
3. Component Evaluation 데이터셋 별도 구축 (등급 판정 검증용, 5~10상품 × 3등급)

### 중기
4. Golden Dataset 재검증 사이클 (3~6개월 주기로 시세 변동 반영)
5. 이미지 base64 어댑터 지원 추가 (로컬 파일 케이스 허용)
6. 예외 run 자동 재시도 지원 (`ExistingResultsIndex` exception 제외 옵션)
7. 데이터셋 확장 (운영 데이터 기반 30 → 50건)

### 장기
8. CI 회귀 자동화 (AI 관련 PR에 한해 서브셋 자동 실행)
9. 비용/latency 메트릭을 raw-results.jsonl 에 포함 (토큰 사용량 어댑터에서 추출)

---

## 10. 부록

### 10-1. 30건 상품 리스트

| # | id | category | tags |
|---|---|---|---|
| 1 | iphone-15-pro-b | ELECTRONICS | — |
| 2 | macbook-pro-14-m3-b | ELECTRONICS | — |
| 3 | playstation-5-disc-b | ELECTRONICS | — |
| 4 | airpods-pro-2-b | ELECTRONICS | — |
| 5 | galaxy-buds-3-pro-a | ELECTRONICS | — |
| 6 | nike-air-force-1-b | FASHION | boundary_price |
| 7 | northface-nuptse-b | FASHION | — |
| 8 | lululemon-align-pants-b | FASHION | — |
| 9 | casio-g-shock-b | FASHION | — |
| 10 | chanel-classic-medium-a | FASHION | vintage_premium, high_value |
| 11 | eames-lounge-chair-b | HOME | vintage_premium, high_value, low_search_volume |
| 12 | ikea-billy-bookcase-b | HOME | low_price |
| 13 | numatic-henry-vacuum-b | HOME | overseas_niche, low_search_volume |
| 14 | dyson-v15-detect-b | HOME | — |
| 15 | balmuda-toaster-a | HOME | — |
| 16 | brompton-m6l-b | SPORTS | vintage_premium |
| 17 | basketball-b | SPORTS | low_price |
| 18 | giant-escape-3-b | SPORTS | — |
| 19 | taylormade-stealth2-driver-b | SPORTS | — |
| 20 | nike-zoomx-vaporfly-3-a | SPORTS | quantity_ambiguous |
| 21 | game-boy-color-b | HOBBY | vintage_premium, discontinued |
| 22 | fender-strat-am-std-b | HOBBY | discontinued, vintage_premium, brand_ambiguous, low_search_volume |
| 23 | polaroid-sx-70-b | HOBBY | vintage_premium |
| 24 | pokemon-151-booster-box-a | HOBBY | quantity_ambiguous |
| 25 | lego-10294-titanic-s | HOBBY | — |
| 26 | la-mer-creme-60ml-a | OTHER | — |
| 27 | sk-ii-facial-230ml-a | OTHER | — |
| 28 | stanley-quencher-40oz-b | OTHER | low_price |
| 29 | diptyque-baies-190g-a | OTHER | — |
| 30 | kinto-travel-tumbler-s | OTHER | low_price |

### 10-2. 주요 파일 참조

| 파일 | 역할 |
|---|---|
| `backend/src/test/resources/ai/golden/cases.jsonl` | Golden Dataset 30건 |
| `backend/src/test/java/com/cos/fairbid/ai/benchmark/` | 러너 전체 패키지 |
| `backend/scripts/replace_image_urls.py` | 기존 ↔ 신규 image_url 매핑 스크립트 |
| `build/benchmark/{timestamp}/` | 실행 결과 산출물 |

### 10-3. 테스트 커버리지

JUnit 기준 55개 pass + 1 gated skip(AiBenchmarkRunnerTest 는 `BENCHMARK_MODELS` 없으면 스킵):

| Suite | Tests |
|---|---:|
| VerdictScorer (strictPass / score100 / iou) | 22 |
| PassAtK | 10 |
| WilsonCI | 5 |
| BenchmarkOrchestrator | 4 |
| ModelAdapterFactory | 5 |
| ReportAggregator | 8 |
| Reporter E2E | 1 |
