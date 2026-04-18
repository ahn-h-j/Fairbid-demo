# AI Assist (AI 경매 어시스턴트) — 스펙 v2

> 상품 이미지와 간략한 정보를 입력하면 AI가 시작가 추천 + 상품 설명을 생성하는 기능.
> 이 문서는 v2 확정 스펙과 설계 이력을 포함한다.

---

## 0. TL;DR

- **입력**: 이미지 + memo + (선택) 카테고리
- **출력**: 추천가 low/mid/high + 상품 설명 + `confidence` (high/low) + `confidenceReason`
- **내부 흐름**: 입력 가드레일 → 1차 Claude(상품 식별+productKey) → **Redis 시세 캐시** → 네이버 검색 → 2차 Claude → 출력 가드레일
- **성능**: 통과율 12/14 (85.7%), 비용 MISS 18원/호출 / HIT 약 4원/호출
- **피드백 루프**: SOFT 위반은 DB 축적 → 매주 월 09:00 KST Discord 리포트 → `/evolve` 수동 분석

---

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

## 6.5. 시세 캐시 (Phase 2)

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

### 검증

`AiAssistServiceCacheTest` 단위 테스트로 3가지 시나리오 검증:
1. 캐시 HIT → 네이버 검색 + 2차 Claude 호출 둘 다 스킵
2. 캐시 MISS → 풀 흐름 실행 후 save() 1회 호출
3. productKey 빈 문자열 → save() 호출 안 함

### 한계 및 고려사항

- **첫 호출은 여전히 느림** — 첫 사용자가 비용 부담. hit률 상승은 운영 초기 곡선
- **설명 재사용** — 같은 상품 다른 사용자가 받으면 설명이 완전 동일. 사용자가 어차피 수정할 거라 허용
- **등급별 독립 키** — B급 hit이어도 A급은 따로 계산. 상품별로 등급 한 번씩은 풀 호출 발생
- **productKey 정규화 품질** — 1차 Claude 가 같은 상품을 매번 같은 키로 정규화해야 hit. 변동성이 있을 수 있음 → 장기적으로 모니터링 필요

---

## 7. 헥사고날 패키지 구조

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

## 8. 기술 스택

| 구성 | 선택 |
|---|---|
| 모델 | Claude Sonnet 4.5 (`claude-sonnet-4-5-20250929`) |
| API | Anthropic Messages API + Vision + Prompt Caching |
| 검색 | 네이버 쇼핑 API + 네이버 카페 API |
| HTTP Client | Spring RestClient (동기, 기존 OAuth2 어댑터와 동일 패턴) |
| DB | MySQL (`guardrail_failure` 테이블) |
| 스케줄러 | Spring `@Scheduled` (ThreadPoolTaskScheduler) |
| 리포트 채널 | Discord Webhook |

**미래 옵션 (모델 독립적 구조)**: `AiClientPort` 구현체만 갈아끼우면 Claude ↔ Gemini ↔ GPT-4o ↔ 로컬(Ollama/Gemma) swap 가능.

---

## 9. 성능 지표 (v1 → v2)

| 지표 | v1 (web_search) | v2 (2단계 + 가드레일) | 개선 |
|---|---|---|---|
| 통과율 (14건 회귀 셋) | 1/14 (rate limit) | **12/14 (85.7%)** | 측정 가능해짐 |
| input_tokens 평균 | 45,757 | **2,472** | **−95%** |
| latency 평균 | 15,321ms | **10,163ms** | **−34%** |
| 호출당 비용 | 약 218원 | **18원** | **−92%** |
| 분당 처리량 (Tier 1, 50K/min) | 1.1건 | **14건+** | **13배** |

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

**관찰:**
- **가격 정확도는 거의 동일** (12/14 케이스가 비슷한 범위에서 PASS)
- **v1 약점**:
  - brompton 예외 발생 — web_search multi-turn 응답 파싱 실패로 보임 (v1의 비결정성 문제)
  - numatic FAIL — 해외 니치 상품에서 v1 도 한계
- **v2 약점**:
  - nike 경계값 실패 — Claude 응답 변동성 (v1 은 100K, v2 는 75K)
  - fender 가 v1 보다 더 멀어짐 (v1 1,250K vs v2 950K) — 단종 모델은 web_search 실시간 검색이 약간 유리

**결론**: **웹서치를 떼서 품질이 떨어진 건 아니다.** 엎치락뒤치락 비슷하고, 오히려 v2 가 1건 더 많이 통과. 결정적 차이는 품질이 아니라 **운영 가능성**:

| 축 | v1 | v2 | 차이 |
|---|---|---|---|
| 통과율 | 11/14 | 12/14 | +1 |
| 호출당 비용 | 218원 | 18원 | **−92%** |
| 분당 처리량 (Tier 1) | 1.1건 | 14건+ | **13배** |
| 예외 발생 | 1/14 (brompton) | 0/14 | v2 더 안정 |

v1 은 품질이 나빠서 못 쓰는 게 아니라 **호출당 45K 토큰 + rate limit + 비결정성** 때문에 운영 불가. v2 는 동등한 품질을 훨씬 적은 비용으로 안정적으로 달성.

---

## 10. 설계 결정 이력

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

SOFT 규칙 (설명 품질 등) 은 매 요청마다 위반 여부만 기록하고 개별 조치는 안 함. 대신 **주간 집계로 반복 패턴 발견 → 프롬프트/규칙 보강** 사이클.

수동 `/evolve` 호출이 기본이지만, 매주 Discord 리포트로 **알림은 자동**. 사용자가 리포트 보고 필요한 주에만 분석.

---

## 11. 다음 단계

### 단기
- ~~**프론트엔드 UI**: `confidence=low` 일 때 ⚠ 배지 + `confidenceReason` 노출~~ — **완료 (2026-04-12)** (`AuctionCreatePage` + `mutations.js`)
- ~~**리뷰 Warning 6건**~~ — **완료 (2026-04-12)**: `@Transactional` 경계, `@CreationTimestamp`, `@Index`, `@Param`, ShedLock, Discord 트랜잭션 분리
- **nike 변동성**: 실행마다 75K/85K/95K 흔들림. 경계값 케이스 불안정. temperature 조정 또는 시드 고정 검토
- **PromptInjectionRule false positive**: `\bsystem:`, `\bassistant:`, `act as` 패턴이 영어 memo 에서 오탐 가능. 줄 시작(`^`) 또는 델리미터 근접 조건으로 제한 검토

### 중기
- ~~**Phase 2 시세 캐시**~~ — **완료 (2026-04-12)**. §6.5 참고
- **카테고리별 검색 파이프라인 튜닝**: `confidence=low` 패턴이 특정 카테고리에서 반복되면 해당 카테고리 전용 검색 전략 추가
- **캐시 hit률 실측**: 운영 데이터로 Zipf 분포 가정이 맞는지 검증. hit률 낮으면 productKey 정규화 품질 개선 필요

### 장기 — 벤더 락인 해소 (이미 설계상 가능)

| 모델 | 입력 ($/M) | 출력 ($/M) | 호출당 비용 | 비고 |
|---|---|---|---|---|
| **Claude Sonnet 4.5** (현재) | 3.00 | 15.00 | 18원 | 벤치 최상위 |
| Claude Haiku 4.5 | 1.00 | 5.00 | ~6원 | 품질 약간 낮음 |
| **Gemini 2.5 Flash** | 0.30 | 2.50 | ~1.7원 | 비용 1/10, 1M context |
| Gemini 2.5 Pro | 1.25 | 10.00 | ~8원 | Sonnet 대비 2~3배 저렴 |
| GPT-4o | 2.50 | 10.00 | ~14원 | 이점 작음 |

**결론**: 현재 FairBid 트래픽 수준에서는 Claude Sonnet 4.5 유지가 합리적. 트래픽이 늘면 Gemini 2.5 Flash/Pro 로 swap 검토 (`AiClientPort` 구현체만 교체하면 됨). 로컬 모델(Gemma 3, Qwen2.5-VL)은 GPU 인프라 부담 때문에 장기 옵션.

---

## 12. 환경 변수

| 변수 | 설명 | 필수 |
|---|---|---|
| `ANTHROPIC_API_KEY` | Claude API 키 | O |
| `NAVER_CLIENT_ID` | 네이버 쇼핑/카페 검색 (OAuth2 와 공유) | O |
| `NAVER_CLIENT_SECRET` | 네이버 검색 시크릿 | O |
| `DISCORD_AI_ASSIST_SOFT_WEBHOOK_URL` | 주간 가드레일 리포트 채널 웹훅 | X (미설정 시 전송 no-op) |
| `DISCORD_AI_ASSIST_SOFT_CRON` | 리포트 cron 오버라이드 (기본 `0 0 9 * * MON`) | X |
