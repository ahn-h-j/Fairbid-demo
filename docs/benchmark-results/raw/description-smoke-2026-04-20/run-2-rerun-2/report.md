# Description Smoke Gate Report

- Cases: 2

## Automated Metrics

| 지표 | Claude | Gemini 2.5 Pro |
|---|---|---|
| 가드레일 위반율 | 1/2 (50.0%) | 1/2 (50.0%) |
| H1 평균 길이 | 0.00자 | 0.00자 |
| 클리셰 평균 | 0.50 | 0.00 |
| memo 재복사(Jaccard) 평균 | 0.00 | 0.00 |
| 성공 케이스 | 2/2 | 2/2 |

## LLM-Judge Absolute (5 criteria × 1~5점)

| 기준 | Claude 평균 | Gemini 평균 |
|---|---|---|
| hook | 2.00 | 2.50 |
| no_spec_dump | 3.50 | 3.50 |
| hidden_value | 3.00 | 3.00 |
| persona_clarity | 4.00 | 4.50 |
| no_reformat | 3.00 | 3.00 |

## LLM-Judge Pairwise (순서 랜덤화 후 생성자별 복구)

| 기준 | Claude 승 | Gemini 승 | TIE | Gemini 승률(비-TIE) |
|---|---|---|---|---|
| hook | 1 | 1 | 0 | 50.0% |
| no_spec_dump | 0 | 2 | 0 | 100.0% |
| hidden_value | 2 | 0 | 0 | 0.0% |
| persona_clarity | 0 | 1 | 1 | 100.0% |
| no_reformat | 1 | 1 | 0 | 50.0% |

## Verdict (SPEC §19 기준)

- 전체 쌍비교 Gemini 승률(비-TIE): 55.6% (claude 4 / gemini 5 / tie 1)
- **판정**: Port 재설계 진행 (≥ 40%)

## Per-Case

### iphone-15-pro-b

- Claude: confidence=high
  - 자동: 위반=[], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=16/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=14/25
- Pairwise: {hook=claude, no_spec_dump=gemini, hidden_value=claude, persona_clarity=TIE, no_reformat=claude}
- Order: A=claude, B=gemini

### taylormade-stealth2-driver-b

- Claude: confidence=high
  - 자동: 위반=[DESCRIPTION_QUALITY, DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=1, Jaccard=0.00
  - Judge 절대: total=15/25
- Gemini 2.5 Pro: confidence=high
  - 자동: 위반=[DESCRIPTION_NO_PERSONA], H1=0자, 클리셰=0, Jaccard=0.00
  - Judge 절대: total=19/25
- Pairwise: {hook=gemini, no_spec_dump=gemini, hidden_value=claude, persona_clarity=gemini, no_reformat=gemini}
- Order: A=gemini, B=claude

