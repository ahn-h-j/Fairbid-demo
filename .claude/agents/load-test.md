# Load Test Agent

k6 부하 테스트를 자동 실행하고 성능 리포트를 생성하는 에이전트.

## 트리거

다음 키워드가 포함된 요청에서 활성화:
- "load test", "부하 테스트"
- "performance test", "성능 테스트"
- "k6"
- "stress test", "스트레스 테스트"

## 설정

| 항목 | 경로 |
|------|------|
| 스크립트 | `k6/scenarios/` |
| 결과 | `k6/results/` |
| 기본 BASE_URL | `http://localhost:8080` |

## 사전 조건

k6 설치 확인:
```bash
k6 version
```

설치 방법:
- macOS: `brew install k6`
- Windows: `choco install k6` 또는 [k6 공식 다운로드](https://k6.io/docs/get-started/installation/)
- Linux: k6 문서 참고

## 사용 가능한 시나리오

| 시나리오 | 파일 | 설명 |
|----------|------|------|
| 일정 부하 | `bid-constant.js` | 100 VU, 2분 지속 입찰 |
| 스파이크 | `bid-spike.js` | 갑작스러운 부하 증가 테스트 |
| 스트레스 | `bid-stress.js` | 점진적 부하 증가로 한계점 탐색 |
| 혼합 부하 | `mixed-load.js` | 다양한 API 동시 호출 |
| WebSocket | `websocket-load.js` | 실시간 WebSocket 연결 테스트 |

## 워크플로우

### 1. 테스트 스크립트 탐색

`k6/scenarios/` 디렉토리에서 `.js` 파일 목록 확인:
```bash
ls k6/scenarios/*.js
```

### 2. 테스트 실행

```bash
k6 run k6/scenarios/{script}.js
```

환경변수로 BASE_URL 변경:
```bash
k6 run -e BASE_URL=http://api.example.com k6/scenarios/{script}.js
```

### 3. 핵심 메트릭 추출

실행 결과에서 다음 메트릭 추출:
- **TPS**: 초당 처리량 (`http_reqs` rate)
- **p95 응답시간**: 95% 요청의 응답 시간
- **평균 응답시간**: 전체 평균
- **실패율**: HTTP 에러 비율
- **총 요청 수**: 테스트 동안 발생한 총 요청

### 4. 리포트 생성

#### 단일 테스트 리포트

파일명: `k6/results/YYYY-MM-DD-{scenario}-load-test.md`

```markdown
# {시나리오} 부하 테스트 결과

## 테스트 환경
- 실행일시: {날짜}
- 대상 URL: {BASE_URL}
- VU (가상 사용자): {vus}
- 테스트 시간: {duration}

## 성능 메트릭

| 메트릭 | 값 |
|--------|-----|
| 총 요청 수 | {count} |
| TPS (초당 처리량) | {rps} |
| 평균 응답시간 | {avg}ms |
| p90 응답시간 | {p90}ms |
| p95 응답시간 | {p95}ms |
| p99 응답시간 | {p99}ms |
| 최대 응답시간 | {max}ms |
| 에러율 | {error_rate}% |

## 분석

{성능 분석 및 병목 지점 설명}

## 권장사항

{개선 제안사항}
```

#### Before/After 비교 리포트

파일명: `k6/results/{feature}-optimization-report.md`

```markdown
# {기능} 성능 최적화 보고서

## 문제점
{최적화 전 발견된 문제}

## 적용된 최적화
- {최적화 항목 1}
- {최적화 항목 2}

## 성능 비교

| 메트릭 | Before | After | 개선율 |
|--------|--------|-------|--------|
| TPS | {before} | {after} | {improvement}% |
| p95 응답시간 | {before}ms | {after}ms | {improvement}% |
| 에러율 | {before}% | {after}% | {improvement}% |

## 결론
{최적화 효과 요약}
```

## 임계값 (Thresholds)

FairBid 프로젝트 SLA (`k6/scenarios/config.js`):
- HTTP 요청: p95 < 500ms, p99 < 1000ms
- 에러율: < 1%
- 입찰 API: p95 < 200ms, p99 < 500ms
- 경매 목록 조회: p95 < 300ms, p99 < 500ms
- WebSocket 연결: p95 < 1000ms

## 주의사항

- VU 수와 테스트 지속시간은 각 스크립트 내 `options`에서 이미 설정됨
- 에이전트는 스크립트 설정을 수정하지 않고 그대로 실행
- 테스트 실행 전 백엔드 서버가 실행 중인지 확인 필요
