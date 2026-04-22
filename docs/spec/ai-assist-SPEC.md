# AI Assist Specification

> 상품 이미지와 간략한 정보를 입력하면 AI가 시작가 추천 + 상품 설명을 생성하는 기능의 전체 스펙. 기능 사양 + 평가 체계 + 모델 선정 근거를 한 문서에 통합한다.

---

## 0. TL;DR

### 기능
- **입력**: 이미지 + memo + (선택) 카테고리
- **출력**: 추천가 low/mid/high + 상품 설명 + `confidence` (high/low) + `confidenceReason`
- **내부 흐름**: 입력 가드레일 → 1차 Claude(상품 식별+productKey) → **Redis 시세 캐시** → 네이버 검색 → 2차 Claude → 출력 가드레일
- **성능**: 통과율 12/14 (85.7%), 비용 MISS 18원/호출 / HIT 약 4원/호출
- **피드백 루프**: SOFT 위반은 DB 축적 → 매주 월 09:00 KST Discord 리포트 → `/evolve` 수동 분석

### 평가 체계
- Golden Dataset 30건 + 3중 지표(Strict / Score100 / IoU) + pass@k/pass^k 기반 벤치마크 러너
- 러너: `backend/src/test/java/com/cos/fairbid/ai/benchmark/` 하위 JUnit
- 실측 결과 아카이브: `docs/benchmark-results/{date}.md`

### 모델 선정
- **프로덕션 모델**: Claude Sonnet 4.5 (`claude-sonnet-4-5-20250929`)
- 근거: Strict 62.7% (1위), 이미지 거부율 0.3%, underpricing 편향(경매 시작가로 안전)
- 상세 실측: `docs/benchmark-results/2026-04-17.md`

---

# Part 1. 기능 스펙

## 1. 기능 개요

### 목적
- 판매자가 적정 시작가를 모를 때, AI가 카테고리/상태/이미지 기반으로 시작가 추천
- 상품 설명(홍보문) 자동 생성으로 등록 허들 완화

### 사용 시나리오
1. 판매자가 등록 폼에 이미지 + 간단 memo 입력
2. "AI 추천 받기" 클릭
3. 1차 Claude 가 이미지 + memo 를 보고 상품 식별 + 중고 등급 판정 + 검색 키워드 생성
4. 네이버 쇼핑/카페 API 로 시세 조회
5. 2차 Claude 가 시세 + 등급을 보고 최종 가격 + 설명 생성
6. 결과가 폼에 자동 채움 (판매자가 수정 가능)

---

## 2. API 명세

### 엔드포인트

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ai/auction-assist` | AI 시작가 추천 + 설명 생성 |

### Request

```json
{
  "category": "ELECTRONICS",
  "memo": "상품 정보: 아이폰 15 Pro 256GB 블루 티타늄\n구매 시기: 2023년 12월\n상태: 양호 (가벼운 사용감)\n추가 정보: 정품 박스, 케이블 포함",
  "imageUrls": [
    "https://cdn.fairbid.com/images/abc123.jpg"
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| category | String (enum) | X | ELECTRONICS / FASHION / HOME / SPORTS / HOBBY / OTHER. 미지정 시 1차 Claude 가 추론 |
| memo | String | X | 구조화 힌트 (상품 정보 / 구매 시기 / 사용 상태 / 추가 정보) |
| imageUrls | List\<String\> | O | 상품 이미지 URL (최소 1장) |

> v1 대비 변경: **title 필드 제거**. 상품 식별은 1차 Claude 가 이미지 + memo 로 수행.

### Response

```json
{
  "success": true,
  "data": {
    "suggestedPrices": {
      "low": 950000,
      "mid": 1100000,
      "high": 1250000
    },
    "generatedDescription": "## 박스도 안 뜯은 맥북 프로 14 M3\n\n2024년에 구매했지만...",
    "confidence": "high",
    "confidenceReason": null
  },
  "serverTime": "2026-04-11T12:00:00",
  "error": null
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| suggestedPrices.low | Long | 보수적 시작가 (mid의 80~90%) |
| suggestedPrices.mid | Long | 적정 시작가 (권장) |
| suggestedPrices.high | Long | 공격적 시작가 (mid의 110~125%) |
| generatedDescription | String | 생성된 상품 설명 (Markdown, 180~450자) |
| confidence | String | `"high"` (검색 결과 기반 확실) / `"low"` (학습 지식 기반 추정) |
| confidenceReason | String | `confidence=low` 일 때만, 불확실한 이유 (프론트 노출용) |

**프론트 처리**: `confidence === "low"` 면 "⚠ 참고용 추정치" 배지 + `confidenceReason` 을 사용자에게 노출.

### Error

| Code | HTTP | 설명 |
|------|------|------|
| AI_SERVICE_UNAVAILABLE | 503 | Claude API 장애/타임아웃/Rate limit |
| AI_GENERATION_FAILED | 502 | 응답 파싱 실패 또는 필드 누락 |
| INVALID_IMAGE | 400 | 이미지 URL 접근 불가 또는 형식 오류 |
| PROMPT_INJECTION_DETECTED | 400 | memo 에서 프롬프트 인젝션 패턴 탐지 |

사용자 메시지는 Claude 가 `need_more_info` / `mismatch` / `image_unreadable` 상태로 직접 작성한 한국어 안내를 그대로 노출한다 (기술 용어 금지).

---

## 3. 내부 흐름 — 2단계 호출 + 가드레일

```
사용자 입력 (memo + 이미지)
    │
    ▼
[입력 가드레일]  PromptInjectionRule (HARD)
    │            한/영 60여 종 인젝션 패턴 탐지 → HTTP 400 즉시 차단
    ▼
[1차 Claude]  auction-assist-phase1.txt
    │         입력: 이미지 + memo (+ category 추론)
    │         출력: productName + grade + searchKeyword + productKey
    ▼
[Redis 시세 캐시]  ai:price:{category}:{productKey}:{grade} 조회 (TTL 7일)
    ├─ HIT  → 저장된 결과 반환 (네이버 검색 + 2차 Claude 스킵)
    │
    └─ MISS → ▼
[네이버 검색]  1차가 생성한 searchKeyword 로 조회
    │         쇼핑 10건 (제목: 가격) + 카페 5건 (중고 거래글)
    │         장애 시 빈 리스트 반환 → Claude 학습 지식으로 fallback
    ▼
[2차 Claude]  auction-assist-system.txt
    │         입력: 1차 결과 + 검색 결과 리스트 전체 + 등급별 보정 범위
    │         출력: low/mid/high + 설명 + confidence(high/low) + confidenceReason
    ▼
[출력 가드레일]
    ├─ PASS → 캐시 적재 (high confidence만) → 반환
    ├─ HARD 위반 → 피드백 append 재시도 1회
    └─ SOFT 위반 → 반환 + guardrail_failure DB INSERT (캐시 적재 X)
                      │
                      ▼
                [주간 리포트 스케줄러] 매주 월 09:00 KST
                      │
                      ▼
                DB 집계 → Discord Webhook 전송 → /evolve 수동 분석
```

---

## 4. 프롬프트 구조

### 4-1. 1차 프롬프트 (`auction-assist-phase1.txt`)

**역할**: 이미지 + memo 를 보고 상품을 식별하고 중고 등급을 판정한다.

**출력 스키마**:
```json
{
  "status": "success",
  "productName": "펜더 아메리칸 스탠다드 스트라토캐스터 일렉트릭 기타",
  "grade": "B",
  "gradeReason": "사용자가 '양호' 상태 명시, 이미지 외관 양호",
  "searchKeyword": "펜더 아메리칸 스탠다드 스트라토캐스터 일렉트릭 기타",
  "productKey": "fender_american_standard_stratocaster"
}
```

**실패 분기**: `need_more_info` / `mismatch` / `image_unreadable` — Claude 가 직접 `userMessage` 작성.

**searchKeyword 생성 규칙**:
- 반드시 **상품 종류** 포함 (기타, 자전거, 진공청소기, 카드 1팩 등) — 부품/액세서리 노이즈 방지
- 색상/구매시기 등 검색 노이즈 제거
- 수량 명시 시 유지 ("1팩", "단품")

**productKey 생성 규칙** (Phase 2 시세 캐시용):
- snake_case (소문자 영문 + 숫자 + 언더스코어)
- 모델 식별자만 포함 — 색상/구매시기/상태/수량은 제외 (동일 상품이 같은 키로 정규화되어야 cache hit)
- 예: `iphone_15_pro_256gb`, `macbook_pro_14_m3_16gb_512gb`, `brompton_m6l`
- 식별 불가 시 빈 문자열 — 캐시 사용 안 함

### 4-2. 2차 프롬프트 (`auction-assist-system.txt`)

**역할**: 1차 결과 + 네이버 검색 결과를 보고 최종 가격 + 마케터 톤 설명 생성.

**등급별 보정 범위 (신품가 대비)**:

| 등급 | 의미 | 범위 |
|---|---|---|
| S | 미개봉/새것 | 80~95% |
| A | 거의 새것 | 65~85% |
| B | 양호 | 55~75% |
| C | 사용감 있음 | 40~60% |
| D | 노후 | 25~45% |

범위를 넓게 겹치게 잡아서 브롬튼 같은 감가 적은 상품도 커버.

**confidence 판정**:
- `"high"` — 검색 결과에 완제품이 있고 상품명/수량/사양 일치
- `"low"` — 부품만 검색, 수량 다름, 단종 모델 등 검색 실패. 학습 지식으로 추정하고 `confidenceReason` 에 구체적 이유

**마케터 체크리스트 (5항목)**:
1. 첫 줄(제목)이 후크인가?
2. 사양 나열이 없는가?
3. 구매자가 모를 수 있는 가치만 설명하는가?
4. 페르소나가 명확한가?
5. 단순 리포맷팅이 아닌가?

### 4-3. Prompt Caching

두 프롬프트 모두 `system` 블록에 `cache_control: {"type": "ephemeral"}` 부여.
- **cache miss (첫 호출)**: 1.25배 비용
- **cache hit (5분 내)**: 0.1배 비용 (90% 할인)
- 토큰 8,181 ~가 cache_read 로 분리 → 호출당 input_tokens 표면값이 약 2K 로 감소

---

## 5. 가드레일 시스템

### 5-1. 입력 가드레일

| 규칙 | Severity | 설명 |
|---|---|---|
| **PromptInjectionRule** | HARD | memo 에서 인젝션 패턴 탐지 → `PromptInjectionDetectedException` (HTTP 400) 즉시 차단 |

**탐지 패턴 (한/영 60여 종)**:
- **지시 덮어쓰기**: "기존 지시 무시", "이전 내용 잊어", "ignore previous instructions", "forget everything"
- **역할 탈취**: "너는 이제", "당신은 이제", "you are now", "act as", "DAN mode", "jailbreak"
- **시스템 프롬프트 노출**: "시스템 프롬프트 보여줘", "reveal prompt"
- **정보 노출**: "DB 덤프", "API 키", "secret", "password"
- **델리미터 주입**: `{{...}}`, `<|...|>`, ` ```system `, `[system]`, `assistant:`, `system:`
- **코드 실행**: "execute code", "run this script"

**설계 원칙**: 보안이라 **false positive 허용하고 강하게 차단**. 정상 중고거래 memo 는 이 패턴과 겹칠 일이 거의 없음.

### 5-2. 출력 가드레일

| 규칙 | Severity | 검사 | 동작 |
|---|---|---|---|
| **PriceStructureRule** | HARD | `low >= mid` / `mid >= high` / 0원/음수 | 재시도 1회 |
| **DescriptionLengthRule** | HARD | 설명 180자 미만 또는 450자 초과 | 재시도 1회 |
| **DescriptionQualityRule** | SOFT | 클리셰(14종), 엔지니어 톤, 불릿 3줄 연속 | DB 기록 |
| **PersonaRule** | SOFT | "~하시는 분", "~하려는 분" 등 페르소나 표현 부재 | DB 기록 |
| **HookRule** | SOFT | 첫 H1 헤딩이 25자 미만 (후크 없음 의심) | DB 기록 |
| **ReformatRule** | SOFT | memo 라인 2개 이상이 설명에 그대로 복사됨 | DB 기록 |
| **ConfidenceTrackingRule** | SOFT | 2차 Claude 가 `confidence=low` 로 자체 마킹 | DB 기록 |

**HARD 재시도 피드백** (user message 끝에 append, system prompt 불변 → prompt cache 유지):
```
[이전 응답 검증 결과]
아래 문제가 발견되어 다시 생성합니다:
- PRICE_INVERSION: low(500,000) >= mid(450,000) — low < mid < high 순서로 생성해주세요
```

**SOFT 는 재시도 안 함** — 질적 판단이라 재시도해도 같은 결과. DB 축적 후 패턴 분석.

재시도는 **최대 1회 고정** (하드코딩, 비용 방어).

---

## 6. 피드백 루프 — 주간 리포트 자동화

### 데이터 수집 (자동)
- 매 요청마다 SOFT 위반 → `guardrail_failure` 테이블 INSERT
- 컬럼: `rule_id`, `severity`, `category`, `keyword`, `violation_message`, `ai_mid_price`, `search_median_price`, `attempt_count`, `created_at`, `resolved`

### 리포트 생성 (스케줄)
- `GuardrailReportScheduler` — Spring `@Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")`
- 매주 월 09:00 KST 지난 7일 집계
- `GuardrailStatsPort` → JPQL 프로젝션 (rule_id 별 / rule × category 별 / 상위 3규칙 샘플 메시지)

### 리포트 전송 (Discord)
- `DiscordReportAdapter` → `discord.ai-assist-soft.webhook-url`
- 환경변수 `DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL` (미설정 시 no-op)
- `ai-assist-soft` 네임스페이스로 다른 Discord 채널과 분리
- 2000자 자동 truncate, 네트워크 실패는 경고 로그만

**리포트 예시**:
```
📊 AI 가드레일 주간 리포트
기간: 2026-04-14 ~ 2026-04-20
총 위반: 47건

규칙별 집계
- DESCRIPTION_QUALITY: 18건
- CONFIDENCE_LOW: 12건
- DESCRIPTION_NO_PERSONA: 8건

규칙 × 카테고리 Top
- DESCRIPTION_QUALITY × HOBBY: 7건
- CONFIDENCE_LOW × HOBBY: 5건

반복 위반 샘플
▸ DESCRIPTION_QUALITY
  - 클리셰 포함: 합리적인 가격에, 강력 추천
  - 엔지니어 톤: Liquid Retina, Apple Silicon
```

### 진화 단계 (수동)
1. Discord 리포트에서 반복 패턴 식별
2. `/evolve` 스킬로 DB + 리포트 분석
3. 규칙/프롬프트 강화 제안 → 사용자 승인
4. 코드 반영 + `resolved=true` 마킹
5. 베이스라인 재측정으로 효과 검증

**수동인 이유**: 프롬프트/규칙 변경은 민감해서 자동 반영 위험. 사용자 판단 후 승인.

---

## 7. 시세 캐시 (Phase 2)

자주 조회되는 상품은 Redis 에 적재해 **2차 Claude 호출 + 네이버 검색을 모두 우회**한다.

### 키 구조

```
ai:price:{category}:{productKey}:{grade}
```

- **category**: `ELECTRONICS` / `FASHION` / `HOME` / `SPORTS` / `HOBBY` / `OTHER`
- **productKey**: 1차 Claude 가 snake_case 로 정규화한 식별자 (예: `iphone_15_pro_256gb`)
- **grade**: `S` / `A` / `B` / `C` / `D` — 등급이 다르면 가격도 다르니 분리

### 값 구조 (JSON)

```json
{
  "low": 900000,
  "mid": 1100000,
  "high": 1300000,
  "description": "## 박스도 안 뜯은 아이폰 15 Pro ...",
  "confidence": "high",
  "confidenceReason": null
}
```

### TTL: 7일
주 단위 시세 변동에 자연스럽게 따라감. 같은 키로 새 응답 오면 덮어쓰기.

### 적재 정책

| 상황 | 캐시 적재 |
|---|---|
| PASS + high confidence | ✅ |
| PASS + low confidence | ❌ (학습 지식 기반 추정이라 시간 지나면 부정확 가능) |
| SOFT 위반 (품질 문제) | ❌ |
| HARD 재시도 소진 | ❌ |
| productKey 빈 문자열 | ❌ (상품 식별 실패) |

### 장애 격리

- Redis 조회 실패 → `Optional.empty()` 반환 → MISS 처리로 풀 흐름 진행
- Redis 적재 실패 → 경고 로그만 남기고 본 흐름 계속
- 즉 캐시는 **순수한 optimization 레이어**, 장애 시에도 기능은 살아있음

### 비용 효과 (이론)

| 시나리오 | 호출당 비용 | latency |
|---|---|---|
| v1 (web_search) | 218원 | 15초 |
| v2 MISS (풀 흐름) | 18원 | 10초 |
| **v2 HIT** | **약 4원** (1차 Claude 만 + Redis 조회) | **약 3초** |

**Zipf 분포 (인기 상품 편중) 가정, hit률 60% 시 평균**: `(4 × 0.6) + (18 × 0.4)` = **약 9.6원/호출** (v2 MISS 대비 추가 −47%, v1 대비 누적 −96%)

### 구현

- `PriceCachePort` (application/port/out/) — `find()` / `save()` 인터페이스
- `RedisPriceCacheAdapter` (adapter/out/cache/) — `StringRedisTemplate` + Jackson 직렬화
- `AiAssistService` 에서 1차 Claude 직후 `find()` → HIT 시 즉시 반환, 가드레일 PASS 후 `save()`

### 한계 및 고려사항

- **첫 호출은 여전히 느림** — 첫 사용자가 비용 부담. hit률 상승은 운영 초기 곡선
- **설명 재사용** — 같은 상품 다른 사용자가 받으면 설명이 완전 동일. 사용자가 어차피 수정할 거라 허용
- **등급별 독립 키** — B급 hit이어도 A급은 따로 계산. 상품별로 등급 한 번씩은 풀 호출 발생
- **productKey 정규화 품질** — 1차 Claude 가 같은 상품을 매번 같은 키로 정규화해야 hit. 변동성이 있을 수 있음 → 장기적으로 모니터링 필요

---

## 8. 헥사고날 패키지 구조

```
ai/
├── domain/
│   ├── AiAssistResult.java            # 추천가 + 설명 + confidence
│   ├── SuggestedPrices.java
│   ├── exception/
│   │   ├── AiGenerationFailedException.java       # 502
│   │   ├── AiServiceUnavailableException.java     # 503
│   │   ├── InvalidImageException.java             # 400
│   │   └── PromptInjectionDetectedException.java  # 400
│   └── guardrail/
│       ├── GuardrailSeverity.java     # HARD / SOFT
│       ├── GuardrailViolation.java
│       ├── OutputValidation.java
│       └── GuardrailWeeklyReport.java
│
├── application/
│   ├── dto/
│   │   ├── AiAssistCommand.java
│   │   ├── PriceItem.java             # 검색 결과 항목
│   │   └── ProductAnalysis.java       # 1차 Claude 결과 (productKey 포함)
│   ├── port/
│   │   ├── in/
│   │   │   ├── GenerateAuctionAssistUseCase.java
│   │   │   └── GenerateGuardrailReportUseCase.java
│   │   └── out/
│   │       ├── AiClientPort.java      # analyzeProduct / generatePricing
│   │       ├── PriceSearchPort.java
│   │       ├── PriceCachePort.java    # Phase 2 — Redis 시세 캐시
│   │       ├── GuardrailFailurePort.java
│   │       ├── GuardrailStatsPort.java
│   │       └── GuardrailReportPort.java
│   └── service/
│       ├── AiAssistService.java       # 2단계 호출 + 가드레일 오케스트레이션
│       ├── GuardrailReportService.java
│       └── guardrail/
│           ├── InputGuardrailChain.java
│           ├── InputGuardrailRule.java
│           ├── OutputGuardrailChain.java
│           └── OutputGuardrailRule.java
│
└── adapter/
    ├── in/
    │   ├── controller/AiAssistController.java
    │   ├── dto/AiAssistRequest.java, AiAssistResponse.java
    │   └── scheduler/GuardrailReportScheduler.java
    └── out/
        ├── claude/
        │   ├── ClaudeApiAdapter.java           # AiClientPort 구현
        │   ├── ClaudePromptBuilder.java        # buildPhase1 / buildPhase2
        │   ├── AnthropicProperties.java
        │   └── dto/
        ├── naver/
        │   ├── NaverShoppingAdapter.java       # PriceSearchPort 구현
        │   ├── NaverSearchProperties.java
        │   └── dto/
        ├── cache/
        │   └── RedisPriceCacheAdapter.java     # PriceCachePort 구현 (Phase 2)
        ├── guardrail/
        │   ├── persistence/
        │   │   ├── GuardrailFailureEntity.java
        │   │   ├── GuardrailFailureRepository.java
        │   │   ├── GuardrailFailurePersistenceAdapter.java
        │   │   ├── GuardrailStatsPersistenceAdapter.java
        │   │   ├── RuleCount.java                 # JPQL 프로젝션
        │   │   └── RuleCategoryCount.java
        │   └── rules/                              # Spring 자동 수집
        │       ├── PromptInjectionRule.java       (입력 HARD)
        │       ├── PriceStructureRule.java        (출력 HARD)
        │       ├── DescriptionLengthRule.java     (출력 HARD)
        │       ├── DescriptionQualityRule.java    (출력 SOFT)
        │       ├── PersonaRule.java               (출력 SOFT)
        │       ├── HookRule.java                  (출력 SOFT)
        │       ├── ReformatRule.java              (출력 SOFT)
        │       └── ConfidenceTrackingRule.java    (출력 SOFT)
        └── discord/
            ├── DiscordProperties.java
            └── DiscordReportAdapter.java          # GuardrailReportPort 구현
```

---

## 9. 기술 스택

| 구성 | 선택 |
|---|---|
| 모델 | Claude Sonnet 4.5 (`claude-sonnet-4-5-20250929`) |
| API | Anthropic Messages API + Vision + Prompt Caching |
| 검색 | 네이버 쇼핑 API + 네이버 카페 API |
| HTTP Client | Spring RestClient (동기, 기존 OAuth2 어댑터와 동일 패턴) |
| DB | MySQL (`guardrail_failure` 테이블) |
| 스케줄러 | Spring `@Scheduled` (ThreadPoolTaskScheduler) |
| 리포트 채널 | Discord Webhook |

**미래 옵션 (프로바이더 통째 swap)**: `AiClientPort` 구현체만 갈아끼우면 Claude ↔ Gemini ↔ GPT-4o ↔ 로컬(Ollama/Gemma) 전체 교체 가능.

> **한계**: 현재 `AiClientPort.generatePricing()` 은 `AiAssistResult`(가격 + 설명 + confidence)를 **번들로 반환**한다. 즉 한 호출에서 한 프로바이더가 둘 다 만든다. "가격은 Claude, 설명은 Gemini" 같은 **역할별 분할**은 빈 교체만으로는 불가능하고, `DescriptionGeneratorPort` 를 신규로 분리하고 `generatePricing` 반환을 가격 전용으로 축소하는 Port 재설계가 필요하다. OTPM 완화·비용 절감용으로 유효한 선택지지만 드롭인은 아니다.

---

## 10. 성능 지표 (v1 → v2)

| 지표 | v1 (web_search) | v2 (2단계 + 가드레일) | 개선 |
|---|---|---|---|
| 통과율 (14건 회귀 셋) | 1/14 (rate limit) | **12/14 (85.7%)** | 측정 가능해짐 |
| input_tokens 평균 | 45,757 | **2,472** | **−95%** |
| latency 평균 | 15,321ms | **10,163ms** | **−34%** |
| 호출당 비용 | 약 218원 | **18원** | **−92%** |
| 분당 처리량 (Tier 1, 30K/min) | 1.1건 | **14건+** | **13배** |

### 실패 2건 (v2 최종)

| id | mid | expected | verdict | confidence | 원인 |
|---|---|---|---|---|---|
| fender-stratocaster | 950K | 1,500K~2,500K | FAIL | low | 단종 모델, 네이버에 신품 없음 → 학습 지식 추정 (이유 노출) |
| nike-air-force-1 | 75K | 80K~140K | FAIL | high | 경계값 5K 부족, Claude 응답 변동성 |

**Low confidence 케이스** (3건, 이 중 2건은 PASS): Claude 가 검색 한계를 스스로 인식하고 학습 지식으로 fallback → 정직한 답변.

### v1 vs v2 품질 직접 비교 (2026-04-12)

> 초기 v1 측정은 rate limit 때문에 1/14 성공이라 품질 비교 불가능했음. 호출 간 120초 sleep + `max_uses=1` 설정으로 v1 을 14건 풀 실행해서 v2 와 나란히 측정.

| id | v1 (web_search) mid | v2 (2단계) mid | expected | 승자 |
|---|---|---|---|---|
| iphone-15-pro | 850K ✅ | 1,200K ✅ | 700K~1,300K | 동률 |
| macbook-pro-14-m3 | 1,880K ✅ | 1,900K ✅ | 1,800K~2,600K | 동률 |
| playstation-5-disc | 380K ✅ | 480K ✅ | 350K~550K | 동률 |
| airpods-pro-2 | 170K ✅ | 175K ✅ | 150K~230K | 동률 |
| nike-air-force-1 | 100K ✅ | 75K ❌ | 80K~140K | **v1** (v2 경계 미달) |
| eames-lounge-chair | 5,600K ✅ | 5,000K ✅ | 4,000K~8,000K | 동률 |
| ikea-billy-bookcase | 45K ✅ | 52K ✅ | 30K~80K | 동률 |
| numatic-henry-vacuum | 240K ❌ | 220K ✅ | 100K~220K | **v2** |
| brompton-bicycle | **ERROR** ❌ | 1,650K ✅ | 1,500K~2,500K | **v2** (v1 예외) |
| basketball | 30K ✅ | 33K ✅ | 15K~50K | 동률 |
| game-boy | 80K ✅ | 65K ✅ | 60K~180K | 동률 |
| fender-stratocaster | 1,250K ❌ | 950K ❌ | 1,500K~2,500K | 둘 다 FAIL (v1 이 더 근접) |
| polaroid-sx-70 | 320K ✅ | 330K ✅ | 200K~500K | 동률 |
| pokemon-tcg-pack | 4,800 ✅ | 4,500 ✅ | 4K~15K | 동률 |

**통과율**: v1 11/14 (78.6%) vs v2 12/14 (85.7%)

**결론**: **웹서치를 떼서 품질이 떨어진 건 아니다.** v1 은 품질이 나빠서 못 쓰는 게 아니라 **호출당 45K 토큰 + rate limit + 비결정성** 때문에 운영 불가. v2 는 동등한 품질을 훨씬 적은 비용으로 안정적으로 달성.

---

# Part 2. 평가 체계

## 11. Golden Dataset

### 11-1. 위치 / 파일

`backend/src/test/resources/ai/golden/cases.jsonl` — JSONL 포맷, 한 줄 = 한 케이스.

### 11-2. 스키마 (v2-lite)

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

### 11-3. 필드 처리 규칙

| 필드 | 처리 |
|---|---|
| `expected.mid` | 저장 안 함, `(low+high)/2` 로 계산 (`Expected.mid()`) |
| `tolerance_pct` | **Score100 도입 후 미사용** (구 Soft PASS 유산, 파싱은 호환 유지) |
| `tags` 누락 | 빈 리스트로 정규화 (`GoldenCase` 컴팩트 생성자) |
| `image_url` | HTTPS 공개 URL 권장. 상대 경로는 API 서버에서 거절됨 (§13-5) |

### 11-4. 카테고리 분포 (5×6=30)

```
ELECTRONICS  5건  — iPhone, MacBook, PS5, AirPods, Galaxy Buds3 Pro
FASHION      5건  — 에어포스, 눕시, 룰루레몬, G-Shock, 샤넬 클래식
HOME         5건  — 임스, 빌리 책장, 뉴마틱, 다이슨 V15, 발뮤다
SPORTS       5건  — 브롬튼, 농구공, 자이언트, 테일러메이드, 베이퍼플라이
HOBBY        5건  — 게임보이, 펜더, 폴라로이드, 포켓몬 박스, 레고 타이타닉
OTHER        5건  — 라메르, SK-II, 스탠리, 딥디크, 킨토
```

### 11-5. tags 분류

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

### 11-6. 데이터 수집 프로세스 (1건당 5~7분)

1. 당근마켓 → 판매완료 필터
2. 가격 10개 수집 (썸네일/제목으로 본체만 확인)
3. 상하위 1개씩 제외 → 중간 8개로 low/high 결정
4. 이상치 제거 (가품/부품/다른 모델)
5. `source` 에 "N건 (X건 제외)" 기록 (추적 가능)

### 11-7. memo 작성 원칙 — 유저 톤

카탈로그 톤이 아니라 실제 판매자가 쓸 법한 짧고 대충된 톤. 등급 표현(B급/양호) 제외 (AI가 memo에서 자체 판정하도록).

---

## 12. 평가 지표

### 12-1. Strict PASS (이분)

```
condition: expected.low ≤ mid ≤ expected.high
score: 1.0 또는 0.0
```

구현: `VerdictScorer.strictPass(gc, mid)`.

### 12-2. Score100 (0~100 연속 점수)

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

### 12-3. IoU (범위 겹침)

```
intersection = max(0, min(rec.high, exp.high) - max(rec.low, exp.low))
union        = max(rec.high, exp.high) - min(rec.low, exp.low)
iou          = intersection / union  (union=0이면 1.0)
```

`{low, mid, high}` 추천 범위 전체 평가. 단일 mid로는 보이지 않는 범위 합리성 체크.

**평균 집계 정책 — Strict PASS run만 포함**: Strict FAIL 케이스는 mid 가 범위 밖이라 대부분 IoU=0. 이를 포함해 평균을 내면 "추천 범위 품질"이 아닌 "Strict PASS rate 종속 지표"가 되어 의미 희석. 따라서 **IoU 평균은 Strict PASS 인 run 만** 집계한다.

### 12-4. pass@k / pass^k (반복 실행)

각 케이스 n회 반복, 통과 횟수 c 기준:

```
pass@1 = c / n
pass@k = 1 - C(n-c, k) / C(n, k),  n-c<k면 1.0
pass^k = (c/n)^k
```

k=3 고정 (LLM eval 관례).

**해석**:
| pass@1 | pass^3 | 진단 |
|---|---|---|
| 1.0 | 1.0 | 완벽 |
| 0.7 | 0.34 | 변동성 문제 (temperature/seed 조정) |
| 0.2 | 0.008 | 능력 부족 (프롬프트/모델 변경) |

### 12-5. Wilson 95% 신뢰구간

```
z = 1.96
denom  = 1 + z²/n
center = (p + z²/(2n)) / denom
margin = z × √(p(1-p)/n + z²/(4n²)) / denom
CI = [center - margin, center + margin]  (n=0이면 [0,1])
```

---

## 13. 벤치마크 러너 아키텍처

### 13-1. 패키지 구조

```
backend/src/test/java/com/cos/fairbid/ai/benchmark/
├── BenchmarkSettings.java              # env var 파싱
├── AiBenchmarkRunnerTest.java          # JUnit 엔트리 (@EnabledIfEnvVar)
├── golden/
│   ├── GoldenCase.java, Expected.java, GoldenCaseLoader.java
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
    ├── ModelReport.java, ReportAggregator.java, MarkdownRenderer.java, Reporter.java
```

### 13-2. 병렬성 정책

- **모델 간 병렬** — `ExecutorService` 로 모든 모델 동시 실행
- **케이스 내 병렬** — 모델당 `CASE_PARALLELISM=3` 스레드 풀에서 3 케이스 동시. 같은 케이스 내 run은 순차
- **총 동시 호출 상한** — 3 모델 × 3 케이스 = **최대 9 파이프라인 동시**
- **러너 자체 재시도 없음** — 프로덕션 가드레일의 1회 HARD 재시도는 그대로. 429/네트워크 오류는 `RawResult.exceptionType`에 기록되고 다음 run으로 넘어감

### 13-3. Rate Limiter — Provider 공유 슬롯

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

**Tier 1 제약 비교**:

| Provider | Tier 1 제약 | 병목 |
|---|---|---|
| Claude Sonnet 4.5 | 50 RPM, 30K ITPM(cache read 무과금), **8K OTPM** | OTPM 타이트 |
| OpenAI gpt-4.1-mini | 500 RPM, 200K TPM | 여유 |
| Gemini 2.5 Flash (paid) | 1000 RPM, 4M TPM | 여유 |

Claude Tier 1 의 OTPM 8K 가 실질 병목. 파이프라인당 output ~1K 토큰 × 분당 3 슬롯 × 2 phase = ~6K OTPM 수준으로 맞추려면 `BENCHMARK_RPM_CLAUDE=5` 권장.

### 13-4. 재개성 — JSONL append + ExistingResultsIndex

- `RawResultWriter.append()` 는 synchronized 로 단일 파일 append
- 러너 시작 시 `ExistingResultsIndex.scan(jsonlFile)` 이 기존 파일을 읽어 `{caseId}#{runIdx}` 키 집합 반환
- 이미 기록된 (case, run) 는 건너뜀 + `SKIP` 로그
- 중단 후 `BENCHMARK_OUTPUT_DIR` 를 같은 경로로 주면 남은 run만 실행

**한계**: 예외(EXCEPTION)도 "완료"로 간주되어 재실행에서 건너뜀. rate-limit 428 등으로 실패한 run을 재시도하려면 해당 줄을 수동 삭제 후 재실행.

### 13-5. 이미지 처리 — `BENCHMARK_SKIP_IMAGES`

Golden dataset 초기 버전은 `image_url: "./images/..."` 상대 경로였으나 Anthropic/OpenAI/Gemini 모두 **HTTPS URL만 허용** ("Only HTTPS URLs are supported").

두 가지 대응:
1. **이미지 포함** (default): `image_url`은 위키피디아 공개 URL로 매핑 (과거 이 매핑은 `backend/scripts/replace_image_urls.py` 스크립트로 수행, 현재는 골든셋에 직접 HTTPS URL 박제됨)
2. **이미지 스킵** (`BENCHMARK_SKIP_IMAGES=true`): `RealModelExecutor.toCommand` 에서 `imageUrls` 를 빈 리스트로 전달. memo 단독 추론

### 13-6. Spring 없는 수동 와이어링

**왜 `@SpringBootTest` 를 쓰지 않는가**
- 각 어댑터(Claude/OpenAI/Gemini)가 `@ConditionalOnProperty("ai.provider", havingValue="...")` 로 가드되어 단일 Spring 컨텍스트에선 **한 provider만 활성화**됨
- 벤치마크는 3 provider 를 동시에 쓰고 싶음

**대응** — `AiBenchmarkRunnerTest` 가 모든 빈을 수동 구성:
- `ModelAdapterFactory` 가 provider별 `AiClientPort` 를 env var API 키로 new 해서 반환
- `InputGuardrailChain` / `OutputGuardrailChain` 은 기존 Rule 구현체들을 `new` 로 묶어서 주입
- `PriceCachePort` 는 `NoOpPriceCachePort` 직접 주입 (Redis 불필요)
- `PriceSearchPort` 는 실제 `NaverShoppingAdapter` 를 네이버 크레덴셜로 구성
- 각 모델마다 `new AiAssistService(...)` 로 독립 인스턴스 조립 → `RealModelExecutor` 로 래핑

**프로덕션 변경 범위** — `ClaudePromptBuilder.loadSystemPrompt()` 의 가시성을 package-private → public 으로 승격 (1줄). 로직 변화 없음.

### 13-7. 출력 구조

```
docs/benchmark-results/
├── runs/{timestamp}/       ← 러너 기본 출력 (gitignored, 스모크·실험용)
│   ├── claude/{ raw-results.jsonl, report.md }
│   ├── openai/{ raw-results.jsonl, report.md }
│   ├── gemini/{ raw-results.jsonl, report.md }
│   └── comparison.md
├── raw/{label}/            ← 본벤치·재검토 raw (git 추적). BENCHMARK_OUTPUT_DIR 로 지정
├── scripts/                ← 집계 스크립트 (bench_analysis.py 등)
└── {date}.md               ← 측정 요약 아카이브
```

> **규칙**: 기본 `runs/` 는 gitignored. 영속화할 측정(본벤치·재검토 트리거)은 실행 시 `BENCHMARK_OUTPUT_DIR=docs/benchmark-results/raw/{label}` 로 지정해 `raw/` 에 직접 떨어뜨리고 커밋한다. 요약 분석은 `{date}.md` 에 수기.

---

## 14. 러너 사용법

### 14-1. 드라이런 (API 키 불필요)

```bash
BENCHMARK_MODELS=claude,openai \
BENCHMARK_DRY_RUN=true \
BENCHMARK_RUNS_PER_CASE=3 \
BENCHMARK_OUTPUT_DIR=build/bench-dryrun \
./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
```

### 14-2. 스모크 (1 케이스 × 1 run × 3 모델)

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

### 14-3. 본 벤치마크 (30 × 10 runs × 3 모델 = 900 pipelines)

```bash
set -a; source .env; set +a
BENCHMARK_MODELS=claude,openai,gemini \
BENCHMARK_RUNS_PER_CASE=10 \
BENCHMARK_CACHE_DISABLED=true \
BENCHMARK_RPM_CLAUDE=5 \
BENCHMARK_OUTPUT_DIR=build/bench-10runs \
./gradlew test --tests 'com.cos.fairbid.ai.benchmark.AiBenchmarkRunnerTest'
```

예상 소요 ~60분. 예상 비용 ~1만 2천 원.

### 14-4. 재개

실행 중단 후 같은 `BENCHMARK_OUTPUT_DIR` 를 지정하면 남은 run만 실행.

### 14-5. 리포트 포맷

**모델별 `report.md`**: Overall (Strict / Score / IoU / pass@k/pass^k / CI) → By Category → By Tag → Bottom 3 Cases → Exceptions

**`comparison.md`**: 모델 비교 표 (Strict / Score / IoU / pass@k / Exceptions) + 모델별 Wilson CI

---

# Part 3. 모델 선정 ADR (2026-04-17)

## 15. 측정 결과 요약

> **전체 raw 데이터 + 케이스별/카테고리별/태그별 상세**: `docs/benchmark-results/2026-04-17.md`

### 15-1. 모델 비교 요약 (900 pipelines)

| Model | Strict Pass | Score | IoU | Exceptions | Avg Latency |
|---|---:|---:|---:|---:|---:|
| **Claude Sonnet 4.5** | **62.7%** | **94.1** | 0.294 | 1 | 13,468ms |
| GPT-5.1 | 37.0% | 82.6 | 0.202 | 10 | 6,623ms |
| Gemini 2.5 Pro | 59.0% | 91.4 | 0.328 | 33 | 19,538ms |

### 15-2. Wilson 95% 신뢰구간

| Model | Strict Pass Rate | 95% Wilson CI |
|---|---:|---|
| Claude Sonnet 4.5 | 62.7% | **57.1% -- 67.9%** |
| GPT-5.1 | 37.0% | **31.7% -- 42.6%** |
| Gemini 2.5 Pro | 59.0% | **53.4% -- 64.4%** |

- Claude vs GPT-5.1: CI 안 겹침, 유의미
- Claude vs Gemini: CI 약간 겹침, 약하게 유의미
- Gemini vs GPT-5.1: CI 안 겹침, 유의미

### 15-3. 핵심 인사이트

1. **이미지 품질이 벤치마크 결과를 지배**: v1 → v2에서 가장 큰 변수는 이미지 교체. exception 해소만으로 전 모델 +18~27pp 상승
2. **Claude는 안정적 1위**: exception 거의 없고(1/300), score 94.1로 가격 정확도 최고
3. **Gemini는 강력한 2위**: v1(31.7%) → v2(59.0%)로 가장 큰 폭 성장
4. **OpenAI는 overpricing 경향**: GPT-5.1으로 업그레이드해도 과대 추정 성향 잔존
5. **여전히 해결 못한 난제**: fender-strat(단종 빈티지 기타), chanel-classic(명품 시세), pokemon-151(TCG 판본 혼동)은 3개 모델 모두 < 20% pass

---

## 16. 의사결정 매트릭스

FairBid AI Assist 의 프로덕션 모델로 **Claude Sonnet 4.5** 를 선정한다.

### 16-1. 선정 요약

| 기준 | 1등 | 비고 |
|---|---|---|
| 정확도 (Strict Pass) | Claude 62.7% | Gemini 59.0% — CI 겹침, 근접 |
| 이미지 관용도 | Claude (0.3% 거부율) | Gemini 11% 거부 — 프로덕션 리스크 |
| 가격 편향 방향 | Claude underpricing | 경매 시작가 추천에 **안전한 방향** |
| 지연 | GPT 6.6s (1등) | Claude 13.5s 중간, Gemini 19.5s 부담 |

단일 지표로는 Claude vs Gemini 박빙이지만, **이미지 관용도 + 편향 방향** 이 사용자 체감과 사업 리스크 측면에서 Claude 를 결정.

### 16-2. Claude 를 선택한 이유

#### (1) 이미지 관용도

| 모델 | Exceptions / 300 | 비율 |
|---|---:|---:|
| **Claude** | **1** | **0.3%** |
| GPT-5.1 | 10 | 3.3% |
| Gemini | 33 | 11.0% |

Gemini 는 이미지가 조금만 애매하면 거부한다. 골든 데이터셋은 사전 검수된 이미지인데도 한 케이스당 9/10 거부 사례 존재.

**프로덕션 시사점**: 실제 유저는 핸드폰으로 대충 찍은 사진을 올린다. 각도/조명/배경 전부 불리. Gemini 의 거부율은 골든 셋(11%) 보다 훨씬 높아질 것.

#### (2) 가격 편향 방향

| 모델 | 편향 패턴 |
|---|---|
| **Claude** | **underpricing** (살짝 낮게) |
| GPT-5.1 | overpricing (크게 높게, 14건 중 11건) |
| Gemini | 편향 적음, 방향 일관성 없음 |

**사업적 의미**:
- 중고 경매 플랫폼의 **시작가 추천**에서 두 오류 비대칭
  - **시작가 너무 높으면** → 입찰이 안 들어옴 → 경매 실패 (치명적)
  - **시작가 너무 낮으면** → 경매 중 자연스럽게 올라감 (복구 가능)
- Claude 의 underpricing 편향 = "안전한 방향의 실수"

#### (3) 지연 (Latency)

| 모델 | 평균 | P95 |
|---|---:|---:|
| GPT-5.1 | **6.6초** | 10초 |
| **Claude** | **13.5초** | 17초 |
| Gemini | 19.5초 | 28초 |

- Gemini 가 가장 느림. 실시간 UX 에서 P95 28초는 사실상 사용 불가 수준
- Claude P95 17초는 "느리지만 수용 가능" 영역
- GPT 가 제일 빠르지만 정확도가 너무 떨어져 선택지에서 탈락

### 16-3. 의사결정 매트릭스 (종합)

| 기준 | 가중치 | Claude | Gemini | GPT-5.1 |
|---|---:|---:|---:|---:|
| 정확도 (Strict Pass) | ★★★ | 62.7 | 59.0 | 37.0 |
| 이미지 관용도 | ★★★ | 99.7 | 89.0 | 96.7 |
| 안전한 편향 방향 | ★★ | ✅ | ➖ | ❌ |
| 지연 | ★ | 13.5s | 19.5s | 6.6s |
| **종합** | — | **1위** | 2위 | 3위 |

- ★★★ 비즈니스 핵심 (틀리거나 거부하면 서비스 실패)
- ★★ 사업 안전성 (틀렸을 때의 피해 방향)
- ★ UX 보조 (느려도 기다리면 됨)

---

## 17. 리스크 + 재검토 트리거

### 17-1. 인지된 리스크

- **Claude Tier 1 의 output TPM 8K** 제약 — 현재 `BENCHMARK_RPM_CLAUDE=5` 로 보호. 프로덕션에서는 (1) Tier 2+/비즈니스 Tier 격상, 또는 (2) **역할 분할**(가격=Claude / 설명=Gemini)로 Claude 출력 토큰을 가격 JSON 수준으로 축소하는 방식 중 선택. 후자는 `AiClientPort` 재설계가 필요하지만 비용도 함께 감소 (설명은 Gemini 가격이 Sonnet 대비 1/10). 상세는 §19 옵션 B 참고
- **Underpricing 누적 영향** — 플랫폼 전반 평균 낙찰가가 시장보다 낮아지는지 모니터링 필요
- **Claude 지연 P95 17초** — 로딩 UX 개선으로 체감 지연 완화 필요 (스켈레톤 / progressive disclosure)

### 17-2. 재검토 트리거

다음 중 하나 이상 충족 시 모델 재평가:
1. Anthropic 신모델 출시 (Claude 4.6 이상) 시 리그레션 벤치마크 즉시 실행
2. 월간 "AI 추천 기각률" 10% 초과 (유저가 수동 수정하는 비율)
3. 신규 카테고리 대거 추가로 기존 golden dataset 커버리지 50% 미만
4. 경쟁 모델의 3rd-party 벤치마크에서 의미 있는 격차 발견

---

# Part 4. 이력 + 로드맵

## 18. 설계 결정 이력

### v1 → v2 전환 동기

**v1 문제 (2026-04-08 베이스라인 측정)**:
- 단일 호출 1건의 input_tokens: **45,757** (스펙 가정 3,000의 15배)
- Anthropic Tier 1 분당 한도(30K)의 **1.5배 초과** → 동시 등록 1건만 와도 429
- 원인: Anthropic `web_search_20250305` 도구의 **multi-turn 결과 누적** — 검색 결과 페이지가 input 에 쌓여 45K 중 약 37K(80%) 차지
- Anthropic API에 검색 결과 토큰 제어 옵션 없음 (손잡이는 `max_uses`, `allowed_domains` 뿐)

**운영 가능성의 문제이지 비용 최적화의 문제가 아님.**

### 핵심 결정: web_search 를 우리 검색 파이프라인으로 분리

```
[v1]  Claude (이미지 분석 + web_search + 매칭 + 산정 + 작성)
      └── ~28K ~ 45K input tokens

[v2]  우리 (memo → 네이버 쇼핑/카페 API → raw 시세 텍스트)
      ↓
      Claude (이미지 분석 + 시세 매칭 + 산정 + 작성, web_search OFF)
      └── ~2.5K input tokens (prompt caching 적용)
```

- AI 역할 6개 중 5개 유지 (검색 1개만 분리)
- 검색은 단순 fetch 라 AI 의 강점이 아닌 영역
- 부수 효과: **LLM 벤더 락인 회피** (AI 인터페이스가 "이미지 + 시세 데이터 → 추천가 + 설명" 으로 표준화)

### 검토했지만 폐기한 옵션

| 옵션 | 결과 |
|---|---|
| `max_uses` 2 → 1 | 45K → 27K, 여전히 분당 한도의 90% |
| `allowed_domains` 4개 제한 | 토큰 절감 0, latency +53% 악화 |
| `max_uses=0` (학습 지식만) | 약 −19K 절감되지만 정확도 큰 손실 |
| Sonnet → Haiku 변경 | 토큰 자체 그대로 |
| System prompt 압축 | −3K, 미미 |
| 이미지 사이즈 캡 | −1.5K, 사용자 입력 통제 안 됨 |

### 2단계 호출 전환 이유

초기 v2 Phase 1 (1단계 호출) 측정에서 실패 3건 분석:
- **brompton**: 경계값
- **fender**: "펜더 스트라토캐스터" 검색 → 저가 라인(스콰이어) 섞임
- **pokemon**: "포켓몬 TCG 부스터팩" 검색 → 30팩 박스 잡힘

**문제**: memo 그대로 검색하면 부품/다른 모델/다른 수량이 섞임. Claude 가 검색 보정 계산기로 전락 ("50~80%로 보정" 고정 규칙), AI 기능이 옅어짐.

**해결**: AI 가 먼저 상품을 식별하고 검색 키워드를 생성 → 검색 → 다시 AI 가 결과를 보고 판단. AI 중심 복원.

### confidence 도입 이유

2단계 호출로도 해결 안 되는 케이스 존재:
- **fender**: 단종 모델, 네이버에 신품 없음
- **pokemon**: 해외직구 가격만 있고 국내 편의점 가격 없음
- **numatic**: 해외 니치 상품

**깨달음**: 검색 결과를 재검색 루프로 고치려 시도 → 같은 결과 반복. 규칙 기반 필터링도 한계. **Claude 가 스스로 판단하게 맡기는 게 가장 깔끔**.

**결과**: `confidence=low` 마킹 + 학습 지식 기반 추정 → pokemon/numatic PASS 로 해결, fender 도 FAIL 이지만 "참고용" 으로 사용자에게 의미 있는 값 제공.

### 피드백 루프 자동화 이유

SOFT 규칙 (설명 품질 등) 은 매 요청마다 위반 여부만 기록하고 개별 조치는 안 함. 대신 **주간 집계로 반복 패턴 발견 → 프롬프트/규칙 보강** 사이클. 수동 `/evolve` 호출이 기본이지만 매주 Discord 리포트로 **알림은 자동**.

### 평가 시스템 재설계 (Golden Dataset + 3중 지표)

14건 회귀 셋의 한계:
1. 통계적 신뢰도 부족 — Wilson 95% CI 폭이 너무 넓어 모델 간 차이 증명 불가
2. P/F 기준 불투명 — expected 범위 근거 없음
3. 경계값 이분법 — 5천원 차이로 결정
4. 단일 지표(mid)만 검증

**재설계**: Golden Dataset 30건 + 3중 지표(Strict / Score100 / IoU) + pass@k/pass^k + Wilson 95% CI.

### Score100 도입 (Soft PASS 대체)

- Soft 3단계로는 "얼마나 빗나갔는지" 구분 불가
- Score100 은 거리 비례 연속, 선형 감쇠
- 학술적으로 Winkler Score(Prediction Interval 평가)를 100점 만점으로 정규화한 변형
- 부작용: 기존 14건 측정과 직접 수치 비교 불가

---

## 19. 다음 단계

### 단기
- ~~**프론트엔드 UI**: `confidence=low` 일 때 ⚠ 배지 + `confidenceReason` 노출~~ — **완료 (2026-04-12)**
- ~~**리뷰 Warning 6건**~~ — **완료 (2026-04-12)**
- ~~**Phase 2 시세 캐시**~~ — **완료 (2026-04-12)**. §7 참고
- **nike 변동성**: 실행마다 75K/85K/95K 흔들림. 경계값 케이스 불안정. temperature 조정 또는 시드 고정 검토
- **PromptInjectionRule false positive**: `\bsystem:`, `\bassistant:`, `act as` 패턴이 영어 memo 에서 오탐 가능. 줄 시작(`^`) 또는 델리미터 근접 조건으로 제한 검토
- **약점 카테고리 프롬프트 개선**: 벤치에서 < 50% 나온 fender/chanel/pokemon — 카테고리 전용 프롬프트 시도

### 중기
- **카테고리별 검색 파이프라인 튜닝**: `confidence=low` 패턴이 특정 카테고리에서 반복되면 해당 카테고리 전용 검색 전략 추가
- **캐시 hit률 실측**: 운영 데이터로 Zipf 분포 가정이 맞는지 검증. hit률 낮으면 productKey 정규화 품질 개선 필요
- **Golden Dataset 재검증 사이클**: 3~6개월 주기로 시세 변동 반영
- **Component Evaluation 데이터셋**: 등급 판정 검증용 (5~10상품 × 3등급)

### 장기 — 멀티 프로바이더 전략

#### 옵션 A. 프로바이더 통째 swap (드롭인, §9 참고)

`AiClientPort` 구현체만 교체. 가격 + 설명을 한 프로바이더가 다 생성.

| 모델 | 입력 ($/M) | 출력 ($/M) | 호출당 비용 | 비고 |
|---|---|---|---|---|
| **Claude Sonnet 4.5** (현재) | 3.00 | 15.00 | 18원 | 벤치 최상위 |
| Claude Haiku 4.5 | 1.00 | 5.00 | ~6원 | 품질 약간 낮음 |
| **Gemini 2.5 Flash** | 0.30 | 2.50 | ~1.7원 | 비용 1/10, 1M context |
| Gemini 2.5 Pro | 1.25 | 10.00 | ~8원 | Sonnet 대비 2~3배 저렴 |
| GPT-4o | 2.50 | 10.00 | ~14원 | 이점 작음 |

#### 옵션 B. 역할별 분할 (Port 재설계 필요)

가격은 Claude, 설명은 Gemini 로 나눠서 각 프로바이더의 강점만 취하는 방식. **OTPM 8K 제약 완화·비용 절감에 가장 효과적**이지만 드롭인 아님.

- **아키텍처 변경**: `generatePricing` 반환을 가격+confidence 전용으로 축소 + `DescriptionGeneratorPort` 신규 분리
- **기대 효과**: Claude 출력 토큰이 가격 JSON(~150토큰)만 남아 OTPM 부담 대폭 감소. 설명은 Gemini 가격이 1/10 수준이라 호출당 비용도 하락
- **리스크**: 설명 가드레일(`DescriptionQualityRule`/`PersonaRule`/`HookRule`/`ReformatRule`/`DescriptionLengthRule`)이 Claude 톤 기준으로 튜닝돼 있어 Gemini 출력에서 위반율 재측정·재튜닝 필요. 가격-설명 톤 정합성(grade/confidence 입력 전달) 유의
- **latency**: Claude phase2 와 Gemini 설명 호출을 병렬화하면 체감 증가 없음

##### 스모크 게이트 (Port 재설계 전 필수)

본 작업(Port 재설계 3~5일) 전에 **가벼운 테스트 코드 범위**로 Gemini 설명 품질을 검증한다. 프로덕션 코드 무수정.

**개발 필요 항목 (~1일)**:

1. **설명-only 생성 유틸** (테스트 코드) — 기존 `ClaudeApiAdapter` / `GeminiApiAdapter` 활용, 입력 `ProductAnalysis + SuggestedPrices` → 출력 마크다운 설명 문자열만. Gemini 측 모델은 **Gemini 2.5 Pro** (벤치 59% 실측. Flash 는 미측정)
2. **`DescriptionQualityScorer` 유틸** — 자동 지표 계산 (가드레일 위반 / 클리셰 / memo 재복사율)
3. **`LlmJudge` 유틸** — **Claude Opus** 호출 + 응답 JSON 파싱
4. **`DescriptionSmokeRunnerTest`** — 10건 케이스 × Claude 1회 + Gemini 2.5 Pro 1회 생성 → 자동 지표 + LLM-judge 실행 → 결과 저장

**자동 지표**:

| 지표 | 계산 | 도구 |
|---|---|---|
| 가드레일 위반율 | `DescriptionQualityRule` / `PersonaRule` / `HookRule` / `ReformatRule` 실행 후 위반 수 | 기존 규칙 재사용 |
| 클리셰 빈도 | `DescriptionQualityRule` 내 14종 클리셰 등장 수 | regex |
| memo 재복사율 | 설명 라인 중 memo 라인과 완전일치 비율 (라인 집합 Jaccard) | set intersection |

> 길이(180~450) 지표는 제외. HARD 가드레일로 이미 100% 필터.
> H1 길이 단독 지표는 제외. 후크 강도는 LLM-judge `hook` 기준에서, 위반 여부는 `HookRule` 카운트로 커버됨.

**LLM-judge 5기준** (스펙 §4-2 마케터 체크리스트):

| ID | 기준 |
|---|---|
| `hook` | 첫 줄이 호기심/설득을 유발하는가 |
| `no_spec_dump` | 사양 나열 없이 가치 중심인가 |
| `hidden_value` | 구매자가 모를 수 있는 장점을 짚었는가 |
| `persona_clarity` | 누가 이 상품을 사야 하는지 명확한가 |
| `no_reformat` | memo 재배열 이상의 창작이 있는가 |

**채점 모드**:
- **절대 점수 (1~5 + reason)** — 베이스라인 기록용
- **쌍비교 (A / B / TIE)** — 같은 케이스의 Claude vs Gemini 나란히, 기준별 승자

**채점자**: **Claude Opus**. 설명 생성자(Claude Sonnet / Gemini Pro) 와 다른 모델로 self-preference 편향 완화. pilot 5건으로 한국어 채점 품질 선검증 후 본 측정 진행. GPT-5.1 은 본 벤치 strict pass 37% 로 한국어 성능 낮아 제외.

**채점 프롬프트 스켈레톤**:

```
[시스템]
너는 중고 거래 플랫폼의 마케팅 카피 에디터다.
제공된 상품 설명을 5개 기준으로 평가하라.
각 점수에 근거 문장 1개 필수.
길이는 평가 대상이 아니다.

기준:
1. hook — 첫 줄이 호기심/설득을 유발하는가
2. no_spec_dump — 사양 나열 없이 가치 중심인가
3. hidden_value — 구매자가 모를 수 있는 장점을 짚었는가
4. persona_clarity — 누가 이 상품을 사야 하는지 명확한가
5. no_reformat — memo 재배열 이상의 창작이 있는가

[절대 점수 모드 — JSON]
{ "hook": { "score": N, "reason": "..." }, ... }

[쌍비교 모드 — JSON]
{ "hook": "A" | "B" | "TIE", ... }

[유저]
상품: {category} / {productName} / 등급 {grade}
설명 (또는 A: ..., B: ...):
---
{description}
---
```

**편향 완화**:

| 편향 | 완화 |
|---|---|
| Position bias (A 위치 고평가) | 쌍비교 시 A/B 순서 50:50 랜덤. 집계 시 원본(claude/gemini)로 복구 |
| Length bias (긴 답변 고점) | 프롬프트에 "길이는 평가 대상 아님" 명시 |
| Self-preference | 생성자(Claude Sonnet / Gemini Pro)와 다른 모델 라인(Claude Opus) judge 사용. Sonnet 출력을 Opus 가 더 선호할 가능성은 pilot 5건에서 점검 |

**산출물 JSON**:

```json
{
  "case_id": "iphone-15-pro-b",
  "run_id": 1,
  "generator": "claude",
  "description": "...",
  "automated": {
    "guardrail_violations": ["PERSONA"],
    "h1_length": 18,
    "cliche_count": 1,
    "reformat_jaccard": 0.15
  },
  "llm_judge_absolute": {
    "hook": { "score": 4, "reason": "..." },
    "no_spec_dump": { "score": 3, "reason": "..." },
    "hidden_value": { "score": 4, "reason": "..." },
    "persona_clarity": { "score": 2, "reason": "..." },
    "no_reformat": { "score": 5, "reason": "..." },
    "total": 18
  },
  "llm_judge_pairwise": {
    "against": "gemini",
    "randomized_order": "A=claude, B=gemini",
    "hook": "A",
    "no_spec_dump": "TIE",
    "hidden_value": "B",
    "persona_clarity": "A",
    "no_reformat": "TIE"
  }
}
```

**판정 기준 (게이트 결과 → 본 작업 결정)**:
- 쌍비교 Gemini 승률(A=Gemini 환산 후) **≥ 40%** → Port 재설계 진행
- 자동 지표에서 Gemini 가드레일 위반율이 Claude 대비 **+10pp 이상** 악화 → 롤백
- 특정 항목 승률 **< 25%** → 해당 항목 Gemini 프롬프트 보강 후 재측정

**스모크용 케이스 10건 선정 원칙** (Golden Dataset 30건 중):
- 카테고리 6종 × 1~2건
- Claude 설명이 벤치에서 다양한 점수 분포를 보인 케이스 우선
- Gemini 가 이미지 거부했던 케이스는 제외 (이미 phase1 에서 걸러지므로 설명 품질 평가 무의미)

**결과 저장**: 기본 `docs/benchmark-results/runs/{timestamp}/` (gitignored). 측정 확정 시 `BENCHMARK_OUTPUT_DIR=docs/benchmark-results/raw/description-smoke-{date}` 로 지정해 `raw/` 에 바로 떨어뜨리고 커밋.

##### 실측 결과 (2026-04-20)

- 케이스: 10건 (run-1). Gemini phase1 이미지 거부로 2건 예외 → run-2 에서 재돌림 (Claude phase1 공유 + 양쪽 phase2 구조)
- judge: Claude Opus 4.7 (`claude-opus-4-7`)
- raw: `docs/benchmark-results/raw/description-smoke-2026-04-20/{run-1-full-10, run-2-rerun-2}/`

**Pairwise (10건 통합, 재돌림 2건 반영)**:

| 기준 | Claude 승 | Gemini 승 | TIE | Gemini 승률(비-TIE) |
|---|---|---|---|---|
| hook | 2 | 5 | 3 | 71.4% |
| no_spec_dump | 0 | 7 | 3 | 100.0% |
| hidden_value | 8 | 2 | 0 | 20.0% ← < 25% |
| persona_clarity | 3 | 6 | 1 | 66.7% |
| no_reformat | 5 | 5 | 0 | 50.0% |
| **합계** | **18** | **25** | **7** | **58.1%** (≥ 40%) |

**절대 점수 평균 (/25)**: Claude 16.6, Gemini 16.8

**판정**: **Port 재설계 진행** (전체 승률 58.1% ≥ 40%).

**잔여 리스크**:
- `hidden_value` 20% < 25% 기준 미달. 다만 샘플 3건 (`ikea-billy-bookcase`, `la-mer-creme-60ml`, `basketball`) 수동 검수 결과 양측 치명적 할루시네이션 없고, 점수 차이는 "Claude 담백 / Gemini 마케팅 톤" 문체 차이가 주요인. 할루시네이션 관점에선 오히려 Claude 가 memo 에 없는 가격 수치("새 공은 10만원이 넘지만")를 언급하는 사례가 있어 주의 필요
- 자동 지표 `guardrailViolations` 는 양쪽 모두 10건 중 7건 위반 (주로 `DESCRIPTION_NO_PERSONA`). 본 작업에서 가드레일 재튜닝 필수

**후속 조치**:
- Port 재설계 본 작업 진입 시 Gemini 프롬프트에 "구매자가 스펙만으로 모를 숨은 가치 강조" 지시 명시적으로 포함 → 재측정 없이 본 작업 내에서 보강
- 본 작업 PR 에 스모크 재실행 서브셋 (2~3건) 포함해 회귀 감시

##### Port 재설계 구현 현황 (2026-04-21, 이슈 #91)

- `AiClientPort.generatePricing` 리턴을 `PricingResult` (가격 + confidence) 로 축소 완료
- `DescriptionGeneratorPort` 신규 인터페이스 + `GeminiDescriptionAdapter` 구현 완료
  - `ai.description.gemini.*` 설정 키 신설, 모델 기본값 `gemini-2.5-pro` 고정
  - 프롬프트 `prompts/auction-assist-description-gemini.txt` 에 `hidden_value` 체크리스트 3번 명시적 강조
  - `ai.provider=claude` (가격) + Description 어댑터 (설명) 조합 상시 활성
- `AiAssistService` phase2a (Claude 가격) + phase2b (Gemini 설명) 순차 호출 + 조립
- 재시도 전략: HARD 위반 시 가격/설명 **둘 다 재호출** (세분화는 후속)

##### 실측 결과 (2026-04-22, `GeminiDescriptionAdapterLiveTest`)

- 환경: Claude Sonnet 4.5 phase1 + Gemini 2.5 Pro 설명. 10건 골드 (`SmokeCaseSelector` 재사용)
- raw: `docs/benchmark-results/raw/description-live-2026-04-22/`

| 지표 | Before (2026-04-20 스모크 Gemini) | After (본 PR) |
|---|---:|---:|
| API 성공률 | 8/10 (이미지 거부 2건) | **10/10** |
| 케이스당 가드레일 위반율 | 70% | **90%** |
| 평균 설명 길이 | ~240자 | 239자 |
| 평균 latency | 19.5s | 16.7s |

성공률 +20pp 개선 (Claude phase1 공유 구조로 Gemini 이미지 거부 해소). 위반율은 +20pp **악화**.

**원인 분석**: 새 프롬프트의 `"모델명 나열 금지"` 가 Gemini 에게 **제품명 완전 제외**로 오해석됨. iphone 케이스에서 후크가 `## 최신 프로 기능, 부담 없이 시작하세요` (14자) 로 나와 `HookRule` (H1 < 25자 SOFT) 걸림. 후크 규칙을 `"제품명(구체 모델) + 매력 포인트 조합, 25자 이상"` 으로 명확화 + 예시 2건 추가 후 재측정 (iphone 1건) 시 후크 `## 최신 기능은 그대로, 아이폰 15 Pro 256GB 블루 티타늄` (35자) 로 개선. 10건 재측정은 후속.

**hidden_value 개선 샘플 확인**: iphone 케이스에서 Gemini 가 `"아이폰 15부터 바뀐 C타입 정품 케이블 → 맥북/아이패드 충전기 통일"` 과 같이 스펙시트에 없는 구체적 가치를 포착. 이전 Claude 설명에는 없던 항목.

**미완 (후속 PR/이슈)**:
- phase2a/phase2b **병렬화** (CompletableFuture). 순차 시 Claude 13s + Gemini 16s 합 ≈ 23s, 병렬화 시 20s 수준 유지 예상
- 가드레일 rule 별 breakdown 집계 + 후크 규칙 수정 반영 10건 재측정 (위반율 회귀 가드)
- 스모크 러너 재설계: 기존 "Claude vs Gemini 설명 비교" 구조가 Port 분리 이후 의미 소실. "프롬프트 A vs B" 비교 구조로 재구성
- Claude phase2 프롬프트 최적화: 설명 지시/응답 스키마 제거로 OTPM·비용 절감 완성

#### 결론

현재 FairBid 트래픽에서는 옵션 A·B 모두 불필요하고 Claude Sonnet 4.5 단독 유지가 합리적. 다만 **Claude Tier 1 OTPM 8K 에 제약이 강해지면 옵션 B 가 Tier 격상보다 우선 검토 대상**이다 (아키텍처 개선 + 비용 하락 동반). 로컬 모델(Gemma 3, Qwen2.5-VL)은 GPU 인프라 부담 때문에 장기 옵션.

### 러너 자체 개선
- 이미지 base64 어댑터 지원 추가 (로컬 파일 케이스 허용)
- 예외 run 자동 재시도 지원 (`ExistingResultsIndex` exception 제외 옵션)
- 데이터셋 확장 (운영 데이터 기반 30 → 50건)
- CI 회귀 자동화 (AI 관련 PR에 한해 서브셋 자동 실행)
- 비용/latency 메트릭을 raw-results.jsonl 에 포함 (토큰 사용량 어댑터에서 추출)

---

## 20. 환경 변수

### 20-1. 런타임 (프로덕션)

| 변수 | 설명 | 필수 |
|---|---|---|
| `ANTHROPIC_API_KEY` | Claude API 키 (phase1 + phase2a 가격) | O |
| `GEMINI_API_KEY` | Gemini API 키 (phase2b 설명 — SPEC §19 옵션 B). `ai.description.gemini.api-key` 가 공유 | O |
| `AI_DESCRIPTION_GEMINI_MODEL` | 설명 생성용 Gemini 모델 | `gemini-2.5-pro` |
| `NAVER_CLIENT_ID` | 네이버 쇼핑/카페 검색 (OAuth2 와 공유) | O |
| `NAVER_CLIENT_SECRET` | 네이버 검색 시크릿 | O |
| `DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL` | 주간 가드레일 리포트 채널 웹훅 | X (미설정 시 전송 no-op) |
| `DISCORD_AI_ASSIST_SOFT_CRON` | 리포트 cron 오버라이드 (기본 `0 0 9 * * MON`) | X |

### 20-2. 벤치마크 러너

| 변수 | 용도 | 기본값 |
|---|---|---|
| `BENCHMARK_MODELS` | 쉼표 구분 모델 (claude, openai, gemini 등) | **필수** |
| `BENCHMARK_RUNS_PER_CASE` | 케이스당 반복 수 | 5 |
| `BENCHMARK_CACHE_DISABLED` | Redis 시세 캐시 우회 (NoOp 강제) | false |
| `BENCHMARK_DRY_RUN` | mock executor 로 파이프라인만 검증 | false |
| `BENCHMARK_OUTPUT_DIR` | 결과 디렉토리 (같은 경로 재지정 시 JSONL append로 재개). 영속화할 측정은 `docs/benchmark-results/raw/{label}` 로 지정 | `docs/benchmark-results/runs/{yyyyMMdd-HHmmss}` (gitignored) |
| `BENCHMARK_CASES_PATH` | Golden JSONL 클래스패스 | `ai/golden/cases.jsonl` |
| `BENCHMARK_CASES_LIMIT` | 앞 N건만 실행 (스모크용) | 전체 |
| `BENCHMARK_SKIP_IMAGES` | `true`면 memo 단독 추론 | false |
| `BENCHMARK_RPM_CLAUDE` | Claude provider 최대 RPM. 0 = 무제한 | 0 |
| `BENCHMARK_RPM_OPENAI` | OpenAI RPM | 0 |
| `BENCHMARK_RPM_GEMINI` | Gemini RPM | 0 |
| `OPENAI_API_KEY` / `GEMINI_API_KEY` | 벤치마크에 해당 모델 포함 시 필수 | — |
