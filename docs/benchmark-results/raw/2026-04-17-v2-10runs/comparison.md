# AI Benchmark — Model Comparison

| Model | Cases | Runs | Strict | Score | IoU | pass@1 | pass@3 | pass^3 | Exceptions |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| claude | 30 | 300 | 62.7% | 94.1 | 0.445 | 0.627 | 0.727 | 0.541 | 1 |
| openai | 30 | 300 | 37.0% | 82.6 | 0.443 | 0.370 | 0.552 | 0.219 | 10 |
| gemini | 30 | 300 | 59.0% | 91.4 | 0.465 | 0.590 | 0.768 | 0.426 | 33 |

_95% Wilson CI per model:_

- **claude**: 57.1% – 67.9%
- **openai**: 31.7% – 42.6%
- **gemini**: 53.4% – 64.4%
