# AI Benchmark Report — openai

- Cases: 30, Total runs: 300, Exceptions: 10

## Overall

| Metric | Value |
|---|---|
| Strict PASS rate | 37.0%  (95% CI 31.7% – 42.6%) |
| Mean Score | 82.6 / 100 |
| Mean IoU | 0.443 |
| pass@1 | 0.370 |
| pass@3 | 0.552 |
| pass^3 | 0.219 |

## By Category

| Bucket | Cases | Runs | Strict | Score | IoU |
|---|---:|---:|---:|---:|---:|
| ELECTRONICS | 5 | 50 | 34.0% | 85.8 | 0.499 |
| SPORTS | 5 | 50 | 50.0% | 91.6 | 0.496 |
| HOBBY | 5 | 50 | 44.0% | 82.4 | 0.351 |
| FASHION | 5 | 50 | 16.0% | 72.0 | 0.333 |
| OTHER | 5 | 50 | 48.0% | 89.2 | 0.505 |
| HOME | 5 | 50 | 30.0% | 74.5 | 0.388 |

## By Tag

| Bucket | Cases | Runs | Strict | Score | IoU |
|---|---:|---:|---:|---:|---:|
| vintage_premium | 6 | 60 | 36.7% | 85.7 | 0.368 |
| discontinued | 2 | 20 | 35.0% | 91.0 | 0.316 |
| boundary_price | 1 | 10 | 20.0% | 89.9 | 0.552 |
| low_price | 4 | 40 | 37.5% | 84.2 | 0.625 |
| quantity_ambiguous | 2 | 20 | 15.0% | 63.9 | 0.313 |
| high_value | 2 | 20 | 20.0% | 72.6 | 0.332 |
| brand_ambiguous | 1 | 10 | 0.0% | 79.3 | 0.000 |
| low_search_volume | 3 | 30 | 13.3% | 50.8 | 0.332 |
| overseas_niche | 1 | 10 | 0.0% | 9.5 | 0.000 |

## Bottom 3 Cases

| Case | Category | Runs | Strict | Score | IoU |
|---|---|---:|---:|---:|---:|
| numatic-henry-vacuum-b | HOME | 10 | 0.0% | 9.5 | 0.000 |
| lululemon-align-pants-b | FASHION | 10 | 0.0% | 32.4 | 0.000 |
| pokemon-151-booster-box-a | HOBBY | 10 | 0.0% | 47.8 | 0.000 |

## Exceptions

| Case | Run | Type | Message |
|---|---:|---|---|
| chanel-classic-medium-a | 2 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| chanel-classic-medium-a | 4 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| taylormade-stealth2-driver-b | 6 | AiServiceUnavailableException | AI 서비스를 일시적으로 사용할 수 없습니다. |
| fender-strat-am-std-b | 2 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 3 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 4 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 5 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 6 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 7 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 10 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
