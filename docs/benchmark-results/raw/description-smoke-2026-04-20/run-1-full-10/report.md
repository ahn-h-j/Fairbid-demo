# Description Smoke Gate Report

- Cases: 10

## Automated Metrics

| 지표 | Claude | Gemini 2.5 Pro |
|---|---|---|
| 가드레일 위반율 | 7/10 (70.0%) | 7/10 (70.0%) |
| H1 평균 길이 | 0.00자 | 0.00자 |
| 클리셰 평균 | 0.10 | 0.00 |
| memo 재복사(Jaccard) 평균 | 0.00 | 0.00 |
| 성공 케이스 | 10/10 | 10/10 |

## LLM-Judge Absolute (5 criteria × 1~5점)

| 기준 | Claude 평균 | Gemini 평균 |
|---|---|---|
| hook | 2.40 | 2.10 |
| no_spec_dump | 4.00 | 3.60 |
| hidden_value | 3.50 | 2.60 |
| persona_clarity | 4.00 | 3.50 |
| no_reformat | 3.00 | 2.70 |

## LLM-Judge Pairwise (순서 랜덤화 후 생성자별 복구)

| 기준 | Claude 승 | Gemini 승 | TIE | Gemini 승률(비-TIE) |
|---|---|---|---|---|
| hook | 3 | 4 | 3 | 57.1% |
| no_spec_dump | 2 | 5 | 3 | 71.4% |
| hidden_value | 8 | 2 | 0 | 20.0% |
| persona_clarity | 5 | 5 | 0 | 50.0% |
| no_reformat | 6 | 4 | 0 | 40.0% |

## Verdict (SPEC §19 기준)

- 전체 쌍비교 Gemini 승률(비-TIE): 45.5% (claude 24 / gemini 20 / tie 6)
- **판정**: Port 재설계 진행 (≥ 40%)

## Per-Case

### iphone-15-pro-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=17/25
- Gemini 2.5 Pro: 예외 AiGenerationFailedException — 입력하신 '아이폰 15 프로 블루 티타늄' 상품과 이미지가 일치하지 않는 것 같아요. 판매하시려는 실제 상품 사진을 올려주시면 더 정확하게 분석해 드릴 수 있습니다.
- Pairwise: {hook=claude, no_spec_dump=claude, hidden_value=claude, persona_clarity=claude, no_reformat=claude}
- Order: A=claude, B=gemini

### airpods-pro-2-b

- Claude: confidence=high
  - 자동: 위반=[], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=16/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=16/25
- Pairwise: {hook=TIE, no_spec_dump=gemini, hidden_value=claude, persona_clarity=claude, no_reformat=claude}
- Order: A=gemini, B=claude

### nike-air-force-1-b

- Claude: confidence=high
  - 자동: 위반=[], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=15/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=20/25
- Pairwise: {hook=gemini, no_spec_dump=gemini, hidden_value=claude, persona_clarity=gemini, no_reformat=gemini}
- Order: A=claude, B=gemini

### chanel-classic-medium-a

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_QUALITY], H1=0자, 클리셰=1, Jaccard=0.00
  - Judge 절대: total=19/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=16/25
- Pairwise: {hook=gemini, no_spec_dump=TIE, hidden_value=claude, persona_clarity=claude, no_reformat=claude}
- Order: A=gemini, B=claude

### ikea-billy-bookcase-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_HOOK, DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=16/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=14/25
- Pairwise: {hook=TIE, no_spec_dump=gemini, hidden_value=gemini, persona_clarity=gemini, no_reformat=claude}
- Order: A=gemini, B=claude

### dyson-v15-detect-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_HOOK, DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=18/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=15/25
- Pairwise: {hook=TIE, no_spec_dump=gemini, hidden_value=claude, persona_clarity=gemini, no_reformat=claude}
- Order: A=claude, B=gemini

### basketball-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_HOOK, DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=17/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_HOOK], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=21/25
- Pairwise: {hook=gemini, no_spec_dump=gemini, hidden_value=claude, persona_clarity=gemini, no_reformat=gemini}
- Order: A=gemini, B=claude

### taylormade-stealth2-driver-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=17/25
- Gemini 2.5 Pro: 예외 AiGenerationFailedException — 입력하신 로프트 각도(10.5도)와 이미지 속 상품의 각도(9.0도)가 다릅니다. 실제 판매하실 상품의 정보와 사진이 일치하는지 다시 한번 확인해주세요.
- Pairwise: {hook=claude, no_spec_dump=claude, hidden_value=claude, persona_clarity=claude, no_reformat=claude}
- Order: A=claude, B=gemini

### polaroid-sx-70-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=17/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=16/25
- Pairwise: {hook=claude, no_spec_dump=TIE, hidden_value=claude, persona_clarity=gemini, no_reformat=gemini}
- Order: A=claude, B=gemini

### la-mer-creme-60ml-a

- Claude: confidence=high
  - 자동: 위반=[], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=17/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_HOOK, DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=17/25
- Pairwise: {hook=gemini, no_spec_dump=TIE, hidden_value=gemini, persona_clarity=claude, no_reformat=gemini}
- Order: A=gemini, B=claude

