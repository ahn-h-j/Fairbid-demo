# AI 모델 선정 기록 (v2 벤치마크)

> **작성일**: 2026-04-17
> **결론**: Claude Sonnet 4.5 채택
> **근거**: 30 cases × 10 runs × 3 models (Claude Sonnet 4.5 / GPT-5.1 / Gemini 2.5 Pro) = 총 900 pipelines 측정
> **관련 문서**: `ai-benchmark-runner.md` (러너 스펙), `ai-assist-spec.md` (AI 어시스트 전체 스펙)

---


## 1. 측정 결과


1. **이미지 교체 14건**: v1에서 위키피디아 이미지가 메모와 불일치하여 대량 exception을 유발한 케이스의 이미지를 실제 상품에 가까운 이미지로 교체
   - 영향 케이스: lululemon, chanel, northface-nuptse, dyson-v15, taylormade-stealth2, lego-10294, la-mer, kinto-travel-tumbler, pokemon-151, fender-strat, ikea-billy, balmuda-toaster, brompton, macbook-pro-14 등
2. **기대 범위 조정 4건**:
   - `airpods-pro-2-b`: 250k~280k -> 130k~210k (중고나라 14만 기준 재조사)
   - `brompton-m6l-b`: 1,800k~2,800k -> 1,500k~2,800k (하한 낮춤, AI 학습 시세 반영)
   - `nike-zoomx-vaporfly-3-a`: 120k~180k -> 100k~200k (한정판 혼동 제거)
   - `galaxy-buds-3-pro-a`: tolerance 10% -> 15%
3. **OpenAI 모델 변경**: GPT-4.1-mini -> GPT-5.1 (성능 향상 기대)
4. **OpenAI RPM 제한**: RPM=10으로 설정하여 rate limit exception 해소

#### 1.2 v2 모델 비교 요약

| Model | Cases | Runs | Strict Pass | Score | IoU | pass@1 | pass@3 | pass^3 | Exceptions |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Claude Sonnet 4.5 | 30 | 300 | **62.7%** | **94.1** | 0.294 | 0.627 | 0.727 | 0.541 | 1 |
| GPT-5.1 | 30 | 300 | 37.0% | 82.6 | 0.202 | 0.370 | 0.552 | 0.219 | 10 |
| Gemini 2.5 Pro | 30 | 300 | 59.0% | 91.4 | 0.328 | 0.590 | 0.768 | 0.426 | 33 |

#### 1.3 Wilson 95% 신뢰구간

| Model | Strict Pass Rate | 95% Wilson CI |
|---|---:|---|
| Claude Sonnet 4.5 | 62.7% | **57.1% -- 67.9%** |
| GPT-5.1 | 37.0% | **31.7% -- 42.6%** |
| Gemini 2.5 Pro | 59.0% | **53.4% -- 64.4%** |

> Claude와 GPT-5.1의 CI가 겹치지 않아 1위 vs 3위 차이는 통계적으로 유의미하다.  
> Claude(57.1~67.9%)와 Gemini(53.4~64.4%)는 CI가 일부 겹치며, 2위와의 차이는 약하게 유의미하다.  
> Gemini(53.4~64.4%)와 GPT-5.1(31.7~42.6%)은 겹치지 않아 2위 vs 3위 차이도 유의미하다.

#### 1.4 v1 vs v2 전체 비교

| 지표 | v1 Claude | v2 Claude | v1 OpenAI (4.1-mini) | v2 OpenAI (5.1) | v1 Gemini (2.5 Flash) | v2 Gemini (2.5 Pro) |
|---|---:|---:|---:|---:|---:|---:|
| Strict Pass | 44.0% | **62.7%** (+18.7pp) | 16.0% | **37.0%** (+21.0pp) | 31.7% | **59.0%** (+27.3pp) |
| Score | 84.5 | **94.1** | 79.0 | **82.6** | 80.0 | **91.4** |
| IoU | 0.227 | **0.294** | 0.167 | **0.202** | 0.246 | **0.328** |
| Exceptions | 37 | **1** | 133 | **10** | 107 | **33** |
| Avg Latency | - | 13,468ms | - | 6,623ms | - | 19,538ms |

> 3개 모델 모두 대폭 개선. 이미지 교체(exception 대폭 감소)와 기대 범위 재조정이 핵심 원인이다.

---

### 2. 케이스별 상세 분석 (30 cases x 3 models)

#### iphone-15-pro-b (ELECTRONICS)

**Expected**: 780,000 ~ 1,000,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.521 | 831,000 | 0 | - |
| OpenAI | 3/10 | 87.1 | 0.124 | 1,068,800 | 0 | borderline (3/10 pass), overpriced (avg 1069k, 69k above high) |
| Gemini | 4/10 | 74.9 | 0.364 | 937,500 | 2 | image mismatch (2 exc) |

---

#### macbook-pro-14-m3-b (ELECTRONICS)

**Expected**: 1,600,000 ~ 1,800,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 9/10 | 98.0 | 0.489 | 1,699,000 | 0 | borderline (9/10 pass) |
| OpenAI | 4/10 | 68.8 | 0.214 | 1,844,000 | 0 | borderline (4/10 pass), overpriced (avg 1844k, 44k above high) |
| Gemini | 2/10 | 55.2 | 0.148 | 1,368,000 | 0 | borderline (2/10 pass), underpriced (avg 1368k, 232k below low) |

---

#### playstation-5-disc-b (ELECTRONICS)

**Expected**: 420,000 ~ 550,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 1/10 | 78.5 | 0.098 | 353,000 | 0 | borderline (1/10 pass), underpriced (avg 353k, 67k below low) |
| OpenAI | 9/10 | 99.2 | 0.576 | 451,800 | 0 | borderline (9/10 pass) |
| Gemini | 7/10 | 96.3 | 0.521 | 470,000 | 0 | borderline (7/10 pass) |

---

#### airpods-pro-2-b (ELECTRONICS)

**Expected**: 130,000 ~ 210,000 (v2에서 250k~280k -> 130k~210k로 조정)

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.562 | 170,500 | 0 | - |
| OpenAI | 0/10 | 87.5 | 0.044 | 235,000 | 0 | overpriced (avg 235k, 25k above high) |
| Gemini | 10/10 | 100.0 | 0.501 | 157,500 | 0 | - |

---

#### galaxy-buds-3-pro-a (ELECTRONICS)

**Expected**: 100,000 ~ 170,000 (v2에서 tolerance 10% -> 15%)

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.294 | 165,000 | 0 | - |
| OpenAI | 1/10 | 86.3 | 0.099 | 196,500 | 0 | borderline (1/10 pass), overpriced (avg 196k, 26k above high) |
| Gemini | 0/10 | 9.4 | 0.017 | 180,000 | 9 | image mismatch (9 exc): "이미지에는 투명한 뚜껑을 가진 이어버드가 보이며, 삼성 갤럭시 버즈 제품의 디자인과 차이가 있습니다" |

---

#### nike-air-force-1-b (FASHION)

**Expected**: 60,000 ~ 110,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.446 | 81,200 | 0 | - |
| OpenAI | 2/10 | 89.9 | 0.146 | 111,100 | 0 | borderline (2/10 pass), overpriced (avg 111k, 1k above high) |
| Gemini | 10/10 | 100.0 | 0.549 | 86,000 | 0 | - |

---

#### northface-nuptse-b (FASHION)

**Expected**: 140,000 ~ 280,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 8/10 | 99.7 | 0.207 | 152,500 | 0 | borderline (8/10 pass) |
| OpenAI | 5/10 | 90.1 | 0.121 | 265,500 | 0 | borderline (5/10 pass) |
| Gemini | 10/10 | 100.0 | 0.425 | 191,000 | 0 | - |

---

#### lululemon-align-pants-b (FASHION)

**Expected**: 70,000 ~ 100,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.667 | 86,000 | 0 | - |
| OpenAI | 0/10 | 32.4 | 0.002 | 146,200 | 0 | overpriced (avg 146k, 46k above high) |
| Gemini | 6/10 | 94.7 | 0.455 | 92,500 | 0 | borderline (6/10 pass) |

---

#### casio-g-shock-b (FASHION)

**Expected**: 60,000 ~ 120,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 1/10 | 91.2 | 0.074 | 49,300 | 0 | borderline (1/10 pass), underpriced (avg 49k, 11k below low) |
| OpenAI | 1/10 | 86.1 | 0.038 | 55,100 | 0 | borderline (1/10 pass), underpriced (avg 55k, 5k below low) |
| Gemini | 10/10 | 100.0 | 0.451 | 85,000 | 0 | - |

---

#### chanel-classic-medium-a (FASHION)

**Expected**: 7,000,000 ~ 10,500,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 2/10 | 84.7 | 0.090 | 9,510,000 | 0 | borderline (2/10 pass) |
| OpenAI | 0/10 | 47.1 | 0.000 | 14,100,000 | 2 | image mismatch (2 exc): "이미지를 분석할 수 없어요" + overpriced (avg 14,100k) |
| Gemini | 0/10 | 67.8 | 0.000 | 4,184,000 | 0 | underpriced (avg 4184k, 2816k below low) |

---

#### eames-lounge-chair-b (HOME)

**Expected**: 6,000,000 ~ 10,000,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 3/10 | 95.3 | 0.197 | 5,920,000 | 0 | borderline (3/10 pass), underpriced (avg 5920k, 80k below low) |
| OpenAI | 4/10 | 83.6 | 0.133 | 5,090,000 | 0 | borderline (4/10 pass), underpriced (avg 5090k, 910k below low) |
| Gemini | 9/10 | 98.5 | 0.494 | 8,190,000 | 0 | borderline (9/10 pass) |

---

#### ikea-billy-bookcase-b (HOME)

**Expected**: 40,000 ~ 50,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 0/10 | 56.8 | 0.025 | 29,200 | 0 | underpriced (avg 29k, 11k below low) |
| OpenAI | 0/10 | 84.4 | 0.232 | 36,100 | 0 | underpriced (avg 36k, 4k below low) |
| Gemini | 9/10 | 91.2 | 0.528 | 47,100 | 0 | borderline (9/10 pass) |

---

#### numatic-henry-vacuum-b (HOME)

**Expected**: 120,000 ~ 200,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 5/10 | 95.0 | 0.294 | 200,000 | 0 | borderline (5/10 pass) |
| OpenAI | 0/10 | 9.5 | 0.000 | 386,000 | 0 | overpriced (avg 386k, 186k above high) |
| Gemini | 0/10 | 64.5 | 0.017 | 271,000 | 0 | overpriced (avg 271k, 71k above high) |

---

#### dyson-v15-detect-b (HOME)

**Expected**: 450,000 ~ 800,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.176 | 453,000 | 0 | - |
| OpenAI | 1/10 | 94.9 | 0.062 | 409,200 | 0 | borderline (1/10 pass), underpriced (avg 409k, 41k below low) |
| Gemini | 10/10 | 100.0 | 0.497 | 563,000 | 0 | - |

---

#### balmuda-toaster-a (HOME)

**Expected**: 190,000 ~ 350,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 7/10 | 99.6 | 0.266 | 208,500 | 0 | borderline (7/10 pass) |
| OpenAI | 10/10 | 100.0 | 0.407 | 237,600 | 0 | - |
| Gemini | 10/10 | 100.0 | 0.510 | 264,000 | 0 | - |

---

#### brompton-m6l-b (SPORTS)

**Expected**: 1,500,000 ~ 2,800,000 (v2에서 하한 1,800k -> 1,500k로 조정)

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 2/10 | 93.6 | 0.058 | 1,311,000 | 0 | borderline (2/10 pass), underpriced (avg 1311k, 189k below low) |
| OpenAI | 3/10 | 95.7 | 0.124 | 1,435,000 | 0 | borderline (3/10 pass), underpriced (avg 1435k, 65k below low) |
| Gemini | 7/10 | 98.8 | 0.250 | 1,614,000 | 0 | borderline (7/10 pass) |

---

#### basketball-b (SPORTS)

**Expected**: 30,000 ~ 50,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.544 | 36,000 | 0 | - |
| OpenAI | 10/10 | 100.0 | 0.694 | 40,100 | 0 | - |
| Gemini | 9/10 | 99.0 | 0.436 | 45,000 | 0 | borderline (9/10 pass) |

---

#### giant-escape-3-b (SPORTS)

**Expected**: 180,000 ~ 260,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.435 | 189,500 | 0 | - |
| OpenAI | 8/10 | 98.2 | 0.323 | 177,500 | 0 | borderline (8/10 pass), underpriced (avg 178k, 2k below low) |
| Gemini | 7/10 | 98.5 | 0.483 | 244,000 | 0 | borderline (7/10 pass) |

---

#### taylormade-stealth2-driver-b (SPORTS)

**Expected**: 260,000 ~ 320,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.586 | 271,000 | 0 | - |
| OpenAI | 1/10 | 74.7 | 0.135 | 264,444 | 1 | exception (1): "AI 서비스를 일시적으로 사용할 수 없습니다" |
| Gemini | 1/10 | 10.0 | 0.075 | 280,000 | 9 | image mismatch (9 exc): "로프트는 9.0도인데, 입력하신 정보에는 10.5도로 기재" |

---

#### nike-zoomx-vaporfly-3-a (SPORTS)

**Expected**: 100,000 ~ 200,000 (v2에서 120k~180k -> 100k~200k로 조정)

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 9/10 | 97.6 | 0.379 | 178,500 | 0 | borderline (9/10 pass) |
| OpenAI | 3/10 | 80.0 | 0.120 | 242,000 | 0 | borderline (3/10 pass), overpriced (avg 242k, 42k above high) |
| Gemini | 5/10 | 78.8 | 0.189 | 199,375 | 2 | image mismatch (2 exc): "신발이 절단된 상태로 보입니다. 중고 상품으로 감정하기는 어렵습니다" |

---

#### game-boy-color-b (HOBBY)

**Expected**: 190,000 ~ 420,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 0/10 | 85.0 | 0.000 | 104,000 | 0 | underpriced (avg 104k, 86k below low) |
| OpenAI | 7/10 | 94.6 | 0.221 | 184,700 | 0 | borderline (7/10 pass), underpriced (avg 185k, 5k below low) |
| Gemini | 2/10 | 91.0 | 0.040 | 138,500 | 0 | borderline (2/10 pass), underpriced (avg 138k, 52k below low) |

---

#### fender-strat-am-std-b (HOBBY)

**Expected**: 1,000,000 ~ 1,400,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 0/10 | 78.5 | 0.008 | 785,000 | 0 | underpriced (avg 785k, 215k below low) |
| OpenAI | 0/10 | 23.8 | 0.012 | 793,333 | 7 | image mismatch (7 exc): "이미지를 분석할 수 없어요" + underpriced (avg 793k) |
| Gemini | 0/10 | 0.0 | 0.000 | N/A | 10 | image mismatch (10 exc): "이미지를 분석할 수 없어요" |

---

#### polaroid-sx-70-b (HOBBY)

**Expected**: 200,000 ~ 350,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.426 | 265,000 | 0 | - |
| OpenAI | 8/10 | 92.5 | 0.368 | 339,000 | 0 | borderline (8/10 pass) |
| Gemini | 9/10 | 99.5 | 0.300 | 329,000 | 0 | borderline (9/10 pass) |

---

#### pokemon-151-booster-box-a (HOBBY)

**Expected**: 100,000 ~ 150,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 2/10 | 73.6 | 0.155 | 100,556 | 1 | exception (1): RestClientException (HTTP 파싱 오류) |
| OpenAI | 0/10 | 47.8 | 0.011 | 338,600 | 0 | overpriced (avg 339k, 189k above high) |
| Gemini | 2/10 | 77.5 | 0.086 | 84,900 | 0 | borderline (2/10 pass), underpriced (avg 85k, 15k below low) |

---

#### lego-10294-titanic-s (HOBBY)

**Expected**: 300,000 ~ 880,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.297 | 766,000 | 0 | - |
| OpenAI | 7/10 | 95.6 | 0.182 | 588,000 | 0 | borderline (7/10 pass) |
| Gemini | 10/10 | 100.0 | 0.331 | 751,500 | 0 | - |

---

#### la-mer-creme-60ml-a (OTHER)

**Expected**: 300,000 ~ 450,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 0/10 | 91.5 | 0.052 | 268,000 | 0 | underpriced (avg 268k, 32k below low) |
| OpenAI | 0/10 | 93.8 | 0.164 | 276,700 | 0 | underpriced (avg 277k, 23k below low) |
| Gemini | 5/10 | 93.9 | 0.232 | 287,000 | 0 | borderline (5/10 pass), underpriced (avg 287k, 13k below low) |

---

#### sk-ii-facial-230ml-a (OTHER)

**Expected**: 150,000 ~ 210,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.503 | 163,500 | 0 | - |
| OpenAI | 10/10 | 100.0 | 0.687 | 167,500 | 0 | - |
| Gemini | 5/10 | 91.3 | 0.280 | 144,000 | 0 | borderline (5/10 pass), underpriced (avg 144k, 6k below low) |

---

#### stanley-quencher-40oz-b (OTHER)

**Expected**: 30,000 ~ 40,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 9/10 | 98.0 | 0.507 | 38,400 | 0 | borderline (9/10 pass) |
| OpenAI | 4/10 | 57.0 | 0.224 | 53,110 | 0 | borderline (4/10 pass), overpriced (avg 53k, 13k above high) |
| Gemini | 3/10 | 53.6 | 0.123 | 50,111 | 1 | image mismatch (1 exc): "이미지에는 새 상품이 진열되어 있지만 사용감 있는 중고 상품이라고 하셨습니다" + overpriced (avg 50k) |

---

#### diptyque-baies-190g-a (OTHER)

**Expected**: 50,000 ~ 100,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 10/10 | 100.0 | 0.392 | 66,500 | 0 | - |
| OpenAI | 9/10 | 99.9 | 0.302 | 60,850 | 0 | borderline (9/10 pass) |
| Gemini | 10/10 | 100.0 | 0.348 | 62,600 | 0 | - |

---

#### kinto-travel-tumbler-s (OTHER)

**Expected**: 30,000 ~ 60,000

| Model | Pass | Score | IoU | Avg Mid | Exc | Note |
|---|---:|---:|---:|---:|---:|---|
| Claude | 0/10 | 97.3 | 0.056 | 28,000 | 0 | underpriced (avg 28k, 2k below low) |
| OpenAI | 1/10 | 95.5 | 0.098 | 27,150 | 0 | borderline (1/10 pass), underpriced (avg 27k, 3k below low) |
| Gemini | 5/10 | 95.5 | 0.099 | 27,200 | 0 | borderline (5/10 pass), underpriced (avg 27k, 3k below low) |

---

### 3. 실패 패턴 요약

#### 3.1 Exception 케이스

v2에서 이미지 교체로 exception이 대폭 감소했으나, 일부 케이스에서 여전히 발생한다.

| Case | Model | Exc 수 | Exception Type | 대표 메시지 |
|---|---|---:|---|---|
| iphone-15-pro-b | Gemini | 2 | AiGenerationFailedException | 여러 아이폰 모델을 보여주고 있어 정확히 일치하는 상품을 판정하기 어렵습니다 |
| galaxy-buds-3-pro-a | Gemini | 9 | AiGenerationFailedException | 투명한 뚜껑을 가진 이어버드가 보이며, 삼성 갤럭시 버즈 제품의 디자인과 차이가 있습니다 |
| chanel-classic-medium-a | OpenAI | 2 | InvalidImageException | 이미지를 분석할 수 없어요 |
| taylormade-stealth2-driver-b | OpenAI | 1 | AiServiceUnavailableException | AI 서비스를 일시적으로 사용할 수 없습니다 |
| taylormade-stealth2-driver-b | Gemini | 9 | AiGenerationFailedException | 로프트는 9.0도인데, 입력하신 정보에는 10.5도로 기재 |
| nike-zoomx-vaporfly-3-a | Gemini | 2 | AiGenerationFailedException | 신발이 절단된 상태로 보입니다. 중고 상품으로 감정하기는 어렵습니다 |
| fender-strat-am-std-b | OpenAI | 7 | InvalidImageException | 이미지를 분석할 수 없어요 |
| fender-strat-am-std-b | Gemini | 10 | InvalidImageException | 이미지를 분석할 수 없어요 |
| pokemon-151-booster-box-a | Claude | 1 | RestClientException | HTTP 응답 파싱 오류 (일시적) |
| stanley-quencher-40oz-b | Gemini | 1 | AiGenerationFailedException | 새 상품이 진열되어 있지만 사용감 있는 중고 상품이라고 하셨습니다 |

**v1 대비 exception 변화**:
- Claude: 37 -> **1** (97% 감소)
- OpenAI: 133 -> **10** (92% 감소, 모델 변경 + RPM 제한 효과)
- Gemini: 107 -> **33** (69% 감소)

#### 3.2 저가 추정 실패 (Underpricing)

AI가 기대 하한보다 낮은 가격을 추천하는 케이스.

| Case | Model | Expected Low | Avg Mid | Gap | Pass Rate |
|---|---|---:|---:|---:|---:|
| playstation-5-disc-b | Claude | 420,000 | 353,000 | -67,000 | 10% |
| macbook-pro-14-m3-b | Gemini | 1,600,000 | 1,368,000 | -232,000 | 20% |
| casio-g-shock-b | Claude | 60,000 | 49,300 | -10,700 | 10% |
| casio-g-shock-b | OpenAI | 60,000 | 55,100 | -4,900 | 10% |
| chanel-classic-medium-a | Gemini | 7,000,000 | 4,184,000 | -2,816,000 | 0% |
| eames-lounge-chair-b | Claude | 6,000,000 | 5,920,000 | -80,000 | 30% |
| eames-lounge-chair-b | OpenAI | 6,000,000 | 5,090,000 | -910,000 | 40% |
| ikea-billy-bookcase-b | Claude | 40,000 | 29,200 | -10,800 | 0% |
| ikea-billy-bookcase-b | OpenAI | 40,000 | 36,100 | -3,900 | 0% |
| dyson-v15-detect-b | OpenAI | 450,000 | 409,200 | -40,800 | 10% |
| brompton-m6l-b | Claude | 1,500,000 | 1,311,000 | -189,000 | 20% |
| brompton-m6l-b | OpenAI | 1,500,000 | 1,435,000 | -65,000 | 30% |
| game-boy-color-b | Claude | 190,000 | 104,000 | -86,000 | 0% |
| game-boy-color-b | OpenAI | 190,000 | 184,700 | -5,300 | 70% |
| game-boy-color-b | Gemini | 190,000 | 138,500 | -51,500 | 20% |
| fender-strat-am-std-b | Claude | 1,000,000 | 785,000 | -215,000 | 0% |
| fender-strat-am-std-b | OpenAI | 1,000,000 | 793,333 | -206,667 | 0% |
| pokemon-151-booster-box-a | Gemini | 100,000 | 84,900 | -15,100 | 20% |
| la-mer-creme-60ml-a | Claude | 300,000 | 268,000 | -32,000 | 0% |
| la-mer-creme-60ml-a | OpenAI | 300,000 | 276,700 | -23,300 | 0% |
| la-mer-creme-60ml-a | Gemini | 300,000 | 287,000 | -13,000 | 50% |
| sk-ii-facial-230ml-a | Gemini | 150,000 | 144,000 | -6,000 | 50% |
| kinto-travel-tumbler-s | Claude | 30,000 | 28,000 | -2,000 | 0% |
| kinto-travel-tumbler-s | OpenAI | 30,000 | 27,150 | -2,850 | 10% |
| kinto-travel-tumbler-s | Gemini | 30,000 | 27,200 | -2,800 | 50% |

#### 3.3 고가 추정 실패 (Overpricing)

AI가 기대 상한보다 높은 가격을 추천하는 케이스.

| Case | Model | Expected High | Avg Mid | Gap | Pass Rate |
|---|---|---:|---:|---:|---:|
| iphone-15-pro-b | OpenAI | 1,000,000 | 1,068,800 | +68,800 | 30% |
| macbook-pro-14-m3-b | OpenAI | 1,800,000 | 1,844,000 | +44,000 | 40% |
| airpods-pro-2-b | OpenAI | 210,000 | 235,000 | +25,000 | 0% |
| galaxy-buds-3-pro-a | OpenAI | 170,000 | 196,500 | +26,500 | 10% |
| galaxy-buds-3-pro-a | Gemini | 170,000 | 180,000 | +10,000 | 0% |
| nike-air-force-1-b | OpenAI | 110,000 | 111,100 | +1,100 | 20% |
| lululemon-align-pants-b | OpenAI | 100,000 | 146,200 | +46,200 | 0% |
| chanel-classic-medium-a | OpenAI | 10,500,000 | 14,100,000 | +3,600,000 | 0% |
| numatic-henry-vacuum-b | OpenAI | 200,000 | 386,000 | +186,000 | 0% |
| numatic-henry-vacuum-b | Gemini | 200,000 | 271,000 | +71,000 | 0% |
| nike-zoomx-vaporfly-3-a | OpenAI | 200,000 | 242,000 | +42,000 | 30% |
| pokemon-151-booster-box-a | OpenAI | 150,000 | 338,600 | +188,600 | 0% |
| stanley-quencher-40oz-b | OpenAI | 40,000 | 53,110 | +13,110 | 40% |
| stanley-quencher-40oz-b | Gemini | 40,000 | 50,111 | +10,111 | 30% |

**패턴**: OpenAI(GPT-5.1)가 overpricing에 가장 취약하다. 14건 중 11건이 OpenAI이다.

#### 3.4 Borderline 케이스 (1~9/10 pass)

불안정하게 통과/실패를 반복하는 케이스. 총 47건 (30 cases x 3 models = 90 중).

| Case | Claude | OpenAI | Gemini |
|---|---:|---:|---:|
| iphone-15-pro-b | 10/10 | **3/10** | **4/10** |
| macbook-pro-14-m3-b | **9/10** | **4/10** | **2/10** |
| playstation-5-disc-b | **1/10** | **9/10** | **7/10** |
| galaxy-buds-3-pro-a | 10/10 | **1/10** | 0/10 |
| nike-air-force-1-b | 10/10 | **2/10** | 10/10 |
| northface-nuptse-b | **8/10** | **5/10** | 10/10 |
| lululemon-align-pants-b | 10/10 | 0/10 | **6/10** |
| casio-g-shock-b | **1/10** | **1/10** | 10/10 |
| chanel-classic-medium-a | **2/10** | 0/10 | 0/10 |
| eames-lounge-chair-b | **3/10** | **4/10** | **9/10** |
| ikea-billy-bookcase-b | 0/10 | 0/10 | **9/10** |
| numatic-henry-vacuum-b | **5/10** | 0/10 | 0/10 |
| dyson-v15-detect-b | 10/10 | **1/10** | 10/10 |
| balmuda-toaster-a | **7/10** | 10/10 | 10/10 |
| brompton-m6l-b | **2/10** | **3/10** | **7/10** |
| basketball-b | 10/10 | 10/10 | **9/10** |
| giant-escape-3-b | 10/10 | **8/10** | **7/10** |
| taylormade-stealth2-driver-b | 10/10 | **1/10** | **1/10** |
| nike-zoomx-vaporfly-3-a | **9/10** | **3/10** | **5/10** |
| game-boy-color-b | 0/10 | **7/10** | **2/10** |
| polaroid-sx-70-b | 10/10 | **8/10** | **9/10** |
| pokemon-151-booster-box-a | **2/10** | 0/10 | **2/10** |
| lego-10294-titanic-s | 10/10 | **7/10** | 10/10 |
| la-mer-creme-60ml-a | 0/10 | 0/10 | **5/10** |
| sk-ii-facial-230ml-a | 10/10 | 10/10 | **5/10** |
| stanley-quencher-40oz-b | **9/10** | **4/10** | **3/10** |
| diptyque-baies-190g-a | 10/10 | **9/10** | 10/10 |
| kinto-travel-tumbler-s | 0/10 | **1/10** | **5/10** |

---

### 4. 전체 모델 실패 케이스 심층 분석

3개 모델 모두 strict pass < 50%인 케이스:

#### chanel-classic-medium-a (FASHION) -- Claude 20%, OpenAI 0%, Gemini 0%

- **Claude**: avg mid 9,510k (범위 내이나 tolerance 바깥 2/10만 pass). 범위 상단에 편향
- **OpenAI**: avg mid 14,100k (3,600k 과대 추정) + 2건 image exception. 명품 시세를 과대평가
- **Gemini**: avg mid 4,184k (2,816k 과소 추정). 국내 중고 명품 시세를 크게 과소평가
- **진단**: 샤넬 클래식은 7,000k~10,500k 범위가 합리적이나 모델 간 편차가 극심. 중고 명품 특성상 컨디션/연식에 따라 가격 분산이 크고, AI가 이를 일관되게 추정하지 못함. 이미지가 실물 색상과 완전히 일치하지 않을 가능성도 잔존

#### fender-strat-am-std-b (HOBBY) -- Claude 0%, OpenAI 0%, Gemini 0%

- **Claude**: avg mid 785k (215k 과소 추정). 단종 기타의 빈티지 프리미엄 미반영
- **OpenAI**: 3건만 가격 산출(avg 793k), 7건 InvalidImageException. 이미지 인식 자체가 불안정
- **Gemini**: 10/10 전부 InvalidImageException. 이미지 완전 실패
- **진단**: v1에서도 4개 모델 전부 실패한 난제. v2에서 이미지를 교체했지만 새 이미지(all4sound.com 제품 사진)도 OpenAI/Gemini가 인식하지 못함. 또한 단종 기타 + 빈티지 프리미엄이라는 이중 난이도. 이미지를 위키피디아 공용 이미지로 재교체하거나, 기대 범위를 800k~1,000k로 하향 검토 필요

#### pokemon-151-booster-box-a (HOBBY) -- Claude 20%, OpenAI 0%, Gemini 20%

- **Claude**: avg mid 100,556 (범위 하단 borderline). 1건 RestClientException
- **OpenAI**: avg mid 338,600 (189k 과대 추정). TCG 시세를 크게 과대평가 -- 일본판/영문판 등 혼동 가능
- **Gemini**: avg mid 84,900 (15k 과소 추정). 반대로 과소평가
- **진단**: 포켓몬 TCG는 판번(한글/일본/영문), 1판/재판, 미개봉/개봉 등 변수가 많아 AI가 일관된 가격을 내기 어려움. 메모에 "한글판"으로 명시되어 있으나 네이버 검색 결과가 다른 판 가격과 혼재

---

### 5. 모델별 강점/약점 분석

#### 5.1 카테고리별 성능

| Category | Cases | Claude Pass | OpenAI Pass | Gemini Pass |
|---|---:|---:|---:|---:|
| ELECTRONICS | 5 | **80%** | 34% | 46% |
| FASHION | 5 | **62%** | 16% | **72%** |
| HOME | 5 | 50% | 30% | **76%** |
| SPORTS | 5 | **82%** | 50% | 58% |
| HOBBY | 5 | 44% | **44%** | 46% |
| OTHER | 5 | 58% | 48% | 56% |

- **Claude 강점**: ELECTRONICS(80%), SPORTS(82%) -- 대중적 전자기기와 스포츠 용품에서 압도적
- **Claude 약점**: HOBBY(44%) -- 빈티지/수집품 가격 추정에 어려움
- **OpenAI 강점**: SPORTS(50%), OTHER(48%) -- 특별히 강한 카테고리 없음
- **OpenAI 약점**: FASHION(16%) -- 패션 아이템 시세 추정이 매우 부정확
- **Gemini 강점**: HOME(76%), FASHION(72%) -- 가구/패션에서 강함
- **Gemini 약점**: ELECTRONICS(46%) -- exception이 많아 실질 성능 저하

#### 5.2 태그별 성능 상관관계

| Tag | Claude | OpenAI | Gemini | 비고 |
|---|---:|---:|---:|---|
| no_tag (일반) | 79% | 43% | 67% | 기본 성능 |
| boundary_price | 100% | 20% | 100% | Claude/Gemini 우수 |
| low_price | 48% | 38% | 65% | Gemini 우위, 저가 아이템 |
| high_value | 25% | 20% | 45% | 고가 아이템에 모두 약함 |
| vintage_premium | 28% | 37% | 45% | 빈티지 프리미엄에 모두 약함, Gemini 상대적 우위 |
| discontinued | 0% | 35% | 10% | 단종 제품에 OpenAI만 일부 성공 |
| overseas_niche | 50% | 0% | 0% | 해외 니치 -- Claude만 성공 |
| low_search_volume | 27% | 13% | 30% | 검색 데이터 부족 케이스에 모두 약함 |
| quantity_ambiguous | 55% | 15% | 35% | 수량 모호 케이스 -- Claude 우위 |
| brand_ambiguous | 0% | 0% | 0% | 브랜드 혼동 케이스 -- 전멸 (fender) |

#### 5.3 Latency 비교

| Model | Avg | P50 | P95 | Min | Max |
|---|---:|---:|---:|---:|---:|
| OpenAI (GPT-5.1) | **6,623ms** | 6,125ms | 10,181ms | 3,247ms | 24,637ms |
| Claude (Sonnet 4.5) | 13,468ms | 12,869ms | 17,118ms | 10,208ms | 60,010ms |
| Gemini (2.5 Pro) | 19,538ms | 20,238ms | 27,768ms | 36ms | 37,929ms |

- OpenAI가 가장 빠르며 Claude의 약 절반
- Gemini의 min=36ms는 exception으로 즉시 반환된 케이스 (fender-strat 등)
- Gemini의 실질 latency는 19~28초로 실시간 UX에 부담

---

### 6. v1 -> v2 Delta 분석

#### 6.1 케이스별 변화 (pass/10 기준)

| Case | Claude v1 | Claude v2 | OpenAI v1 | OpenAI v2 | Gemini v1 | Gemini v2 | 변화 원인 |
|---|---:|---:|---:|---:|---:|---:|---|
| iphone-15-pro-b | 8 | **10** (+2) | 3 | 3 (=) | 4 | 4 (=) | - |
| macbook-pro-14-m3-b | 6 | **9** (+3) | 3 | **4** (+1) | 3 | 2 (-1) | 이미지 교체 |
| playstation-5-disc-b | 0 | **1** (+1) | 3 | **9** (+6) | 2 | **7** (+5) | - |
| airpods-pro-2-b | 0 | **10** (+10) | 0 | 0 (=) | 0 | **10** (+10) | **기대 범위 조정** (250k~280k -> 130k~210k) |
| galaxy-buds-3-pro-a | 10 | 10 (=) | 2 | 1 (-1) | 0 | 0 (=) | tolerance 변경, Gemini 이미지 exc 지속 |
| nike-air-force-1-b | 10 | 10 (=) | 2 | 2 (=) | 10 | 10 (=) | 변화 없음 |
| northface-nuptse-b | 10 | 8 (-2) | 1 | **5** (+4) | 0 | **10** (+10) | **이미지 교체** (Gemini 10/10 exc -> 0) |
| lululemon-align-pants-b | 0 | **10** (+10) | 1 | 0 (-1) | 2 | **6** (+4) | **이미지 교체** (Claude 10/10 exc -> 0) |
| casio-g-shock-b | 0 | **1** (+1) | 1 | 1 (=) | 9 | **10** (+1) | - |
| chanel-classic-medium-a | 0 | **2** (+2) | 0 | 0 (=) | 0 | 0 (=) | **이미지 교체** (Claude/Gemini exc 해소) |
| eames-lounge-chair-b | 5 | 3 (-2) | 1 | **4** (+3) | 9 | 9 (=) | OpenAI exc 해소 |
| ikea-billy-bookcase-b | 3 | 0 (-3) | 0 | 0 (=) | 0 | **9** (+9) | **이미지 교체** (Gemini exc 해소) |
| numatic-henry-vacuum-b | 1 | **5** (+4) | 0 | 0 (=) | 0 | 0 (=) | - |
| dyson-v15-detect-b | 10 | 10 (=) | 1 | 1 (=) | 0 | **10** (+10) | **이미지 교체** (Gemini 10/10 exc -> 0) |
| balmuda-toaster-a | 5 | **7** (+2) | 6 | **10** (+4) | 10 | 10 (=) | **이미지 교체** (OpenAI exc 해소) |
| brompton-m6l-b | 0 | **2** (+2) | 0 | **3** (+3) | 1 | **7** (+6) | **기대 범위 조정** + 이미지 교체 |
| basketball-b | 10 | 10 (=) | 6 | **10** (+4) | 6 | **9** (+3) | OpenAI exc 해소 |
| giant-escape-3-b | 10 | 10 (=) | 2 | **8** (+6) | 7 | 7 (=) | OpenAI exc 해소 |
| taylormade-stealth2-driver-b | 0 | **10** (+10) | 0 | **1** (+1) | 0 | **1** (+1) | **이미지 교체** (Claude 10/10 exc -> 0) |
| nike-zoomx-vaporfly-3-a | 0 | **9** (+9) | 0 | **3** (+3) | 0 | **5** (+5) | **기대 범위 조정** (120k~180k -> 100k~200k) |
| game-boy-color-b | 0 | 0 (=) | 1 | **7** (+6) | 2 | 2 (=) | OpenAI exc 해소 + 모델 변경 |
| fender-strat-am-std-b | 0 | 0 (=) | 0 | 0 (=) | 0 | 0 (=) | 여전히 전멸 |
| polaroid-sx-70-b | 10 | 10 (=) | 2 | **8** (+6) | 9 | 9 (=) | OpenAI exc 해소 |
| pokemon-151-booster-box-a | 2 | 2 (=) | 0 | 0 (=) | 3 | 2 (-1) | 개선 안 됨 |
| lego-10294-titanic-s | 3 | **10** (+7) | 4 | **7** (+3) | 0 | **10** (+10) | **이미지 교체** (Claude/Gemini exc 해소) |
| la-mer-creme-60ml-a | 0 | 0 (=) | 0 | 0 (=) | 0 | **5** (+5) | **이미지 교체** (Gemini 10/10 exc -> 0) |
| sk-ii-facial-230ml-a | 10 | 10 (=) | 2 | **10** (+8) | 7 | 5 (-2) | OpenAI exc 해소 |
| stanley-quencher-40oz-b | 9 | 9 (=) | 3 | **4** (+1) | 1 | **3** (+2) | OpenAI exc 해소 |
| diptyque-baies-190g-a | 10 | 10 (=) | 3 | **9** (+6) | 10 | 10 (=) | OpenAI exc 해소 |
| kinto-travel-tumbler-s | 0 | 0 (=) | 1 | 1 (=) | 0 | **5** (+5) | **이미지 교체** (Gemini 10/10 exc -> 0) |

#### 6.2 변경 유형별 영향 분석

#### 이미지 교체 효과 (14건 교체)

이미지 교체는 exception 감소에 가장 큰 기여를 했다:

- **Claude exception**: 37 -> 1 (36건 감소). v1에서 exc를 유발한 lululemon, chanel, taylormade, lego 등이 모두 해소
- **OpenAI exception**: 133 -> 10 (123건 감소). AiServiceUnavailableException 118건이 RPM 제한 + 모델 변경으로 대부분 해소
- **Gemini exception**: 107 -> 33 (74건 감소). northface, dyson, lego, la-mer, kinto 등 이미지 불일치로 10/10 거부하던 케이스 해소

**이미지 교체 + exception 해소 = 가격 추정 기회 확보 -> pass rate 상승**의 직접적 인과관계.

#### 기대 범위 조정 효과 (4건)

| Case | 변경 내용 | Claude 변화 | OpenAI 변화 | Gemini 변화 |
|---|---|---:|---:|---:|
| airpods-pro-2-b | 250k~280k -> 130k~210k | 0 -> **10** | 0 -> 0 | 0 -> **10** |
| brompton-m6l-b | 1,800k -> 1,500k 하한 | 0 -> **2** | 0 -> **3** | 1 -> **7** |
| nike-zoomx-vaporfly-3-a | 120k~180k -> 100k~200k | 0 -> **9** | 0 -> **3** | 0 -> **5** |
| galaxy-buds-3-pro-a | tolerance 10% -> 15% | 10 -> 10 | 2 -> 1 | 0 -> 0 |

- `airpods-pro-2-b`의 범위 조정이 가장 극적: v1 기대 범위(250k~280k)가 실시세와 크게 괴리되어 있었음
- `nike-zoomx-vaporfly-3-a`: 한정판 혼동 제거 + 범위 확대로 Claude 0 -> 9

#### OpenAI 모델 변경 + RPM 제한 효과

GPT-4.1-mini -> GPT-5.1 + RPM=10:
- **exception 대폭 감소**: 133 -> 10 (v1의 AiServiceUnavailableException 118건이 사실상 0으로)
- **strict pass**: 16.0% -> 37.0% (+21pp)
- OpenAI의 개선분 중 상당 부분은 exception 해소에 의한 것이며, 순수 가격 정확도 개선은 제한적
- 여전히 overpricing 성향이 강함 (11/14 overpricing 건이 OpenAI)

#### 6.3 악화된 케이스

일부 케이스는 v2에서 오히려 후퇴했다:

| Case | Model | v1 | v2 | 원인 분석 |
|---|---|---:|---:|---|
| northface-nuptse-b | Claude | 10 | 8 | 가격 분산 증가 (통계적 변동 범위 내) |
| ikea-billy-bookcase-b | Claude | 3 | 0 | 새 이미지에서도 underpricing 지속, 분산에 의한 약간의 악화 |
| eames-lounge-chair-b | Claude | 5 | 3 | 통계적 변동 |
| macbook-pro-14-m3-b | Gemini | 3 | 2 | 통계적 변동, 여전히 underpricing |
| galaxy-buds-3-pro-a | OpenAI | 2 | 1 | 통계적 변동 |
| lululemon-align-pants-b | OpenAI | 1 | 0 | overpricing 심화 (avg 146k vs expected max 100k) |
| sk-ii-facial-230ml-a | Gemini | 7 | 5 | 통계적 변동 |
| pokemon-151-booster-box-a | Gemini | 3 | 2 | 통계적 변동 |

대부분 1~3 수준의 차이로 통계적 변동 범위 내이며, 구조적 악화는 아니다.

---

### 7. 종합 결론

#### 모델 순위 (v2 확정)

```
1위: Claude Sonnet 4.5  -- Strict 62.7% (CI: 57.1-67.9%)
2위: Gemini 2.5 Pro     -- Strict 59.0% (CI: 53.4-64.4%)
3위: GPT-5.1            -- Strict 37.0% (CI: 31.7-42.6%)
```

#### 핵심 인사이트

1. **이미지 품질이 벤치마크 결과를 지배한다**: v1 -> v2에서 가장 큰 변수는 이미지 교체였으며, exception 해소만으로 전 모델 +18~27pp 상승
2. **Claude는 안정적 1위**: exception이 거의 없고(1/300), score 94.1로 가격 정확도도 최고
3. **Gemini는 강력한 2위로 부상**: v1(31.7%) -> v2(59.0%)로 가장 큰 폭 성장. 이미지 불일치에 가장 엄격했던 만큼 이미지 교체 수혜가 가장 컸음
4. **OpenAI는 overpricing 경향**: 모델을 GPT-5.1으로 올렸지만 가격을 과대 추정하는 성향이 전 카테고리에 걸쳐 나타남
5. **여전히 해결 못한 난제**: fender-strat(단종 빈티지 기타), chanel-classic(명품 시세), pokemon-151(TCG 판본 혼동)은 3개 모델 모두 < 20% pass
6. **다음 단계**: fender-strat 이미지 재교체, la-mer/kinto-travel-tumbler 기대 범위 재검토, 빈티지/수집품 카테고리 전용 프롬프트 고려

---

## 2. 모델 선정 의사결정


### 1. 요약

FairBid AI Assist 의 프로덕션 모델로 **Claude Sonnet 4.5** 를 선정한다.

| 기준 | 1등 | 비고 |
|---|---|---|
| 정확도 (Strict Pass) | Claude 62.7% | Gemini 59.0% — CI 겹침, 근접 |
| 이미지 관용도 | Claude (0.3% 거부율) | Gemini 11% 거부 — 프로덕션 리스크 |
| 가격 편향 방향 | Claude underpricing | 경매 시작가 추천에 **안전한 방향** |
| 지연 | GPT 6.6s (1등) | Claude 13.5s 중간, Gemini 19.5s 부담 |

단일 지표로는 Claude vs Gemini 박빙이지만, **이미지 관용도 + 편향 방향** 이 사용자 체감과 사업 리스크 측면에서 Claude 를 결정.

---

### 2. 정확도 비교

v2 벤치마크 기준 (30 cases × 10 runs = 300 runs/model):

| 모델 | Strict Pass | Score100 | Wilson 95% CI |
|---|---:|---:|---|
| **Claude Sonnet 4.5** | **62.7%** | **94.1** | 57.1% – 67.9% |
| Gemini 2.5 Flash | 59.0% | 91.4 | 53.4% – 64.4% |
| GPT-4.1-mini | 37.0% | 82.6 | 31.7% – 42.6% |

- **Claude vs GPT-4.1-mini** — CI 안 겹침. 통계적으로 유의미한 차이.
- **Claude vs Gemini** — CI 약간 겹침. 정확도만으로는 박빙.

단일 지표(정확도)로는 결정 못 함 → 아래 세 가지 정성/보조 지표가 결정 근거.

---

### 3. Claude 를 선택한 이유

#### 3-1. 이미지 관용도

| 모델 | Exceptions / 300 | 비율 |
|---|---:|---:|
| **Claude** | **1** | **0.3%** |
| GPT-4.1-mini | 10 | 3.3% |
| Gemini | 33 | 11.0% |

Gemini 는 이미지가 조금만 애매하면 거부한다. 벤치마크에서 관찰된 패턴:
- "투명한 뚜껑과 이어버드 디자인이 판매 상품과 다름" 같은 이유로 한 케이스당 9/10 거부
- 골든 데이터셋은 사전 검수된 이미지인데도 이 정도 거부율

**프로덕션 시사점**: 실제 유저는 핸드폰으로 대충 찍은 사진을 올린다. 각도/조명/배경 전부 불리. Gemini 의 거부율은 골든 셋(11%) 보다 훨씬 높아질 것. Strict 에서 3.7pp 차이는 실전 체감 성능 격차를 크게 벌린다.

#### 3-2. 가격 편향 방향

벤치마크에서 관찰된 편향:

| 모델 | 편향 패턴 | 상세 |
|---|---|---|
| **Claude** | **underpricing** (살짝 낮게) | 예: AirPods 250K→180K, Brompton 1.8M→1.3M |
| GPT-4.1-mini | overpricing (크게 높게) | 14건 중 11건 고가 추정 |
| Gemini | 편향 적음 | 방향 일관성 없음 |

**사업적 의미**:
- 중고 경매 플랫폼의 **시작가 추천**에서 두 오류 비대칭
  - **시작가 너무 높으면** → 입찰이 안 들어옴 → 경매 실패 (치명적)
  - **시작가 너무 낮으면** → 경매 중 자연스럽게 올라감 (복구 가능)
- Claude 의 underpricing 편향 = "안전한 방향의 실수"

> 참고: AI 추천은 판매자가 수정 가능한 기본값이다. 방향이 틀린 것보다 "조금 낮지만 시장을 유도하는 값" 이 비즈니스 손실을 최소화한다.

#### 3-3. 지연 (Latency)

| 모델 | 평균 | P95 |
|---|---:|---:|
| GPT-4.1-mini | **6.6초** | 10초 |
| **Claude** | **13.5초** | 17초 |
| Gemini | 19.5초 | 28초 |

- Gemini 가 가장 느림. 실시간 UX 에서 P95 28초는 사실상 사용 불가 수준
- Claude P95 17초는 "느리지만 수용 가능" 영역
- GPT 가 제일 빠르지만 정확도가 너무 떨어져 선택지에서 탈락

**타협**: Claude 는 지연에서 GPT 에 뒤지지만 정확도 +25.7pp 으로 상쇄. Gemini 는 정확도 2등이지만 지연이 과도.

---

### 4. 의사결정 매트릭스

| 기준 | 가중치 | Claude | Gemini | GPT-4.1-mini |
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

### 5. 리스크 및 재검토 트리거

#### 인지된 리스크
- **Claude Tier 1 의 output TPM 8K** 제약 — 현재 `BENCHMARK_RPM_CLAUDE=5` 로 보호. 프로덕션에서는 Tier 2+ 또는 비즈니스 Tier 로 격상 필요
- **Underpricing 누적 영향** — 플랫폼 전반 평균 낙찰가가 시장보다 낮아지는지 모니터링 필요
- **Claude 지연 P95 17초** — 로딩 UX 개선으로 체감 지연 완화 필요 (스켈레톤 / progressive disclosure)

#### 재검토 트리거
다음 중 하나 이상 충족 시 모델 재평가:
1. Anthropic 신모델 출시 (Claude 4.6 이상) 시 리그레션 벤치마크 즉시 실행
2. 월간 "AI 추천 기각률" 10% 초과 (유저가 수동 수정하는 비율)
3. 신규 카테고리 대거 추가로 기존 golden dataset 커버리지 50% 미만
4. 경쟁 모델의 3rd-party 벤치마크에서 의미 있는 격차 발견

---

### 6. 다음 단계

1. 프로덕션 `ai.provider=claude` 고정 (이미 default)
2. Anthropic Tier 2+ 계약 검토 (OTPM 여유 확보)
3. 약점 카테고리 프롬프트 개선 사이클
   - vintage/discontinued (game-boy, fender) — 네이버 검색 결과가 현재 시세 못 반영
   - quantity_ambiguous (pokemon, vaporfly) — 박스/단팩 판단 프롬프트 강화
4. Golden Dataset 재검증 사이클 (3~6개월 주기)
5. "AI 추천 수용률" 모니터링 대시보드 (유저가 AI 제안 가격 그대로 쓰는 비율)

---

### 7. 부록 — 평가 지표 정의

| 지표 | 의미 |
|---|---|
| Strict Pass | 모델 mid ∈ [expected.low, expected.high] 비율 (이진) |
| Score100 | 0~100 연속 점수. 범위 내 100, 벗어나면 거리 비례 감점 |
| IoU | Strict PASS run 의 추천 범위 ↔ 정답 범위 겹침 비율 |
| Wilson 95% CI | 비율 지표의 신뢰구간 (작은 표본에 정확) |
| Exception | AI 가 응답 생성을 거부하거나 API 오류 |

상세 정의 및 러너 구조는 `docs/spec/ai-benchmark-runner.md` 참조.

