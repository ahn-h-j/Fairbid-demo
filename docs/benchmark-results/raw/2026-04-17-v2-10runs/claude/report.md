# AI Benchmark Report — claude

- Cases: 30, Total runs: 300, Exceptions: 1

## Overall

| Metric | Value |
|---|---|
| Strict PASS rate | 62.7%  (95% CI 57.1% – 67.9%) |
| Mean Score | 94.1 / 100 |
| Mean IoU | 0.445 |
| pass@1 | 0.627 |
| pass@3 | 0.727 |
| pass^3 | 0.541 |

## By Category

| Bucket | Cases | Runs | Strict | Score | IoU |
|---|---:|---:|---:|---:|---:|
| ELECTRONICS | 5 | 50 | 80.0% | 95.3 | 0.476 |
| SPORTS | 5 | 50 | 82.0% | 98.2 | 0.485 |
| HOBBY | 5 | 50 | 44.0% | 89.2 | 0.388 |
| FASHION | 5 | 50 | 62.0% | 95.1 | 0.463 |
| OTHER | 5 | 50 | 58.0% | 97.4 | 0.480 |
| HOME | 5 | 50 | 50.0% | 89.3 | 0.318 |

## By Tag

| Bucket | Cases | Runs | Strict | Score | IoU |
|---|---:|---:|---:|---:|---:|
| vintage_premium | 6 | 60 | 28.3% | 89.5 | 0.424 |
| discontinued | 2 | 20 | 0.0% | 81.8 | 0.000 |
| boundary_price | 1 | 10 | 100.0% | 100.0 | 0.446 |
| low_price | 4 | 40 | 47.5% | 88.0 | 0.548 |
| quantity_ambiguous | 2 | 20 | 55.0% | 90.1 | 0.463 |
| high_value | 2 | 20 | 25.0% | 90.0 | 0.501 |
| brand_ambiguous | 1 | 10 | 0.0% | 78.5 | 0.000 |
| low_search_volume | 3 | 30 | 26.7% | 89.6 | 0.480 |
| overseas_niche | 1 | 10 | 50.0% | 95.0 | 0.448 |

## Bottom 3 Cases

| Case | Category | Runs | Strict | Score | IoU |
|---|---|---:|---:|---:|---:|
| ikea-billy-bookcase-b | HOME | 10 | 0.0% | 56.8 | 0.000 |
| playstation-5-disc-b | ELECTRONICS | 10 | 10.0% | 78.5 | 0.588 |
| fender-strat-am-std-b | HOBBY | 10 | 0.0% | 78.5 | 0.000 |

## Exceptions

| Case | Run | Type | Message |
|---|---:|---|---|
| pokemon-151-booster-box-a | 1 | RestClientException | Error while extracting response for type [com.cos.fairbid.ai.adapter.out.claude.dto.ClaudeMessageResponse] and content type [application/octet-stream] |
