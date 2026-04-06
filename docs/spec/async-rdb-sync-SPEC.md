# 비동기 RDB 동기화 테스트 Specification

> ⚠️ **이 문서는 향후 진행할 테스트의 계획서입니다. 아직 실행 전이며, 실행 후 별도 결과 문서를 작성할 예정입니다.**
> 동기 → @Async → Message Queue 단계별 문제 해결 과정을 테스트로 증명하고, 데이터 기반으로 최적의 아키텍처를 선택한다.

---

## 1. Overview

### Problem Statement

현재 동기 방식의 입찰 플로우에서 **장애 전파로 인한 전체 서비스 마비** 문제가 있다:

```
[동기 방식의 진짜 문제: 장애 전파]

@Transactional (클래스 레벨)
public Bid placeBid(...) {
    // ⚠️ 메서드 진입 시점에 이미 DB 커넥션 획득 시도!

    Redis Lua 실행  ← DB 커넥션 얻은 후에야 실행
    bidRepository.save(bid)
}

DB 장애 발생 시:
1. 입찰 요청 → @Transactional 진입 → 커넥션 풀에서 커넥션 요청
2. connection-timeout: 3초 동안 블로킹 (모든 요청이!)
3. Redis 작업조차 시작 못함 (DB 커넥션 대기 중)
4. 커넥션 풀 50개 고갈 → 새 요청도 3초씩 대기
5. 스레드 풀 점유 → 다른 API(경매 조회 등)도 영향

→ 결과: 단순 "20건 유실"이 아닌 "입찰 API 전체 마비"
→ 핵심 수치: p95 응답시간 N배 급증, TPS 급락
```

### Solution Summary

**단계별 전환으로 문제 해결**:

```
[Phase 1: 동기 (현재)]
문제: DB 장애 → 전체 API 마비 (장애 전파)
수치: p95 Nms → Nms, TPS N → N 급락

[Phase 2: @Async]
해결: 장애 격리 성공 (DB 장애가 응답에 영향 없음)
한계: 앱 종료 시 메모리 큐 유실 → N건 영구 손실
수치: 유실 N건, 복구 불가

[Phase 3: MQ (Redis Stream / Kafka / RabbitMQ)]
해결: 장애 격리 + 복구 가능성
수치: 유실 0건, 재시작 후 N초 내 자동 복구
```

### Success Criteria

| 기준 | Phase 1 (동기) | Phase 2 (@Async) | Phase 3 (MQ) |
|------|----------------|------------------|--------------|
| 장애 격리 | ❌ 전체 마비 | ✅ 격리됨 | ✅ 격리됨 |
| 복구 가능성 | ❌ | ❌ 메모리 유실 | ✅ 자동 복구 |
| 핵심 측정 지표 | p95 급증, TPS 급락 | 유실 건수 | 복구 시간 |

---

## 2. 테스트 범위

### 2.1 비교 대상 (5가지)

| 단계 | 방식 | 목적 |
|------|------|------|
| Baseline | 동기 (현재) | 문제점 증명 |
| Step 1 | @Async | 비동기 전환, 메모리 기반 한계 증명 |
| Step 2-A | Redis Stream | 기존 인프라 활용 |
| Step 2-B | Kafka | 대규모 시스템 표준 |
| Step 2-C | RabbitMQ | 균형 잡힌 선택지 |

### 2.2 구현 방식

- 기존 프로젝트에서 **구현체 교체**하며 테스트 (락 테스트 때처럼)
- 각 방식별 브랜치 또는 프로파일로 분리
- 동일한 테스트 시나리오로 비교

### 2.3 테스트 환경

- **로컬 Docker** (docker-compose)
- k6 부하 테스트 도구
- **테스트 데이터 시드**: k6 `setup()` 함수에서 API 호출로 테스트 유저/경매 생성

---

## 2-1. 관측 인프라

### 커스텀 Micrometer 메트릭

BidService에 아래 메트릭을 추가하여 Prometheus → Grafana로 수집:

| 메트릭 | 타입 | 용도 |
|--------|------|------|
| `fairbid_bid_total` | Counter (tag: result=success/fail) | 입찰 성공/실패 건수 |
| `fairbid_bid_rdb_sync_seconds` | Timer | RDB 동기화 소요 시간 |
| `fairbid_bid_redis_count` | Gauge | Redis에 있는 총 입찰 수 |
| `fairbid_bid_rdb_count` | Gauge | RDB에 있는 총 입찰 수 |
| `fairbid_bid_inconsistency_count` | Gauge | Redis - RDB 차이 (불일치 건수) |

### Grafana 대시보드 추가 패널

**Row: 입찰 메트릭**

| 패널 | PromQL | 증명 목적 |
|------|--------|----------|
| 입찰 처리량 (TPS) | `rate(fairbid_bid_total[1m])` by result | 성공/실패 비율 실시간 |
| RDB 동기화 지연 | `fairbid_bid_rdb_sync_seconds` p95/p99 | DB 상태에 따른 동기화 시간 변화 |
| 입찰 API 응답시간 | `http_server_requests` uri="/api/v1/bids" | 입찰 엔드포인트 전용 레이턴시 |

**Row: Redis-RDB 정합성**

| 패널 | PromQL | 증명 목적 |
|------|--------|----------|
| Redis vs RDB 입찰 건수 | `fairbid_bid_redis_count` vs `fairbid_bid_rdb_count` | 두 라인이 벌어지면 불일치 |
| 불일치 건수 | `fairbid_bid_inconsistency_count` | 불일치 발생 순간 스파이크 |

### Grafana Annotation

장애 주입/복구 시점을 그래프에 수직선으로 표시하여 시각적 인과관계를 명확히 함:

```bash
# 장애 주입 시점에 Annotation 생성
curl -X POST http://localhost:3001/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"text":"DB 장애 주입 (docker pause mysql)","tags":["fault-injection"]}'

# 복구 시점에 Annotation 생성
curl -X POST http://localhost:3001/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"text":"DB 복구 (docker unpause mysql)","tags":["recovery"]}'
```

---

## 3. 판단 기준 (우선순위 순)

### 3.1 1순위: 장애 시 복구 가능성

```
측정 방법:
1. 부하 테스트 중 앱 강제 종료 (docker kill)
2. 재시작 후 미처리 메시지 재처리 여부 확인
3. 최종적으로 Redis-RDB 정합성 확인

성공 기준: 재시작 후 자동 복구 (Eventual Consistency)
- 메시지 유실 없이 브로커에 보존
- 재시작 후 Pending 메시지 처리
- 최종적으로 RDB에 이력 저장 완료
```

### 3.2 2순위: 장애 격리

```
측정 방법:
1. 부하 테스트 중 DB 컨테이너 정지 (docker pause mysql)
2. DB 다운 중 입찰 요청 성공률 측정
3. HTTP 응답 코드 확인 (200 vs 500)

성공 기준: DB 다운 중에도 HTTP 200 반환
```

### 3.3 3순위: 복구 시간

```
측정 방법:
1. 앱 재시작 후 Pending 메시지 처리 시간 측정
2. 정상 상태 복구까지 소요 시간

성공 기준: 재시작 후 자동 복구
```

### 3.4 4순위: 운영 복잡도

```
평가 항목:
- 설정 복잡도 (설정 파일 라인 수)
- 모니터링 용이성 (기본 제공 UI)
- 장애 대응 난이도 (문서화 수준)
- 학습 곡선 (첫 경험 기준)
```

### 3.5 참고: 성능 (동기 vs 비동기 비교에만 사용)

```
동기 vs 비동기 비교 시:
- TPS: 초당 처리량
- p95/p99 latency: 응답 시간

MQ 간 비교에서는 의미 없음:
- 발행만 하고 바로 응답하므로 거의 동일
```

---

## 4. 테스트 시나리오

### Phase 1: 동기 방식 문제점 증명

#### Baseline 측정 (정상 상태)

```bash
# 정상 상태에서 30초 부하 → 기준선 확보
k6 run --duration 30s k6/scenarios/bid-sync-test.js
```

**목적**: 장애 주입 전후를 비교하기 위한 기준값 확보
**기록 항목**: TPS, p95 응답시간, 에러율 0%, Redis-RDB 불일치 0건

#### 테스트 1: DB 다운 시 불일치 + 응답 시간 전파

```bash
# 1. Grafana Annotation: 장애 주입 시점 기록
curl -X POST http://localhost:3001/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"text":"DB 장애 주입","tags":["fault-injection"]}'

# 2. 부하 테스트 중 DB 정지
docker pause fairbid-mysql

# 3. 10초간 장애 유지 (입찰 요청 계속 유입)

# 4. Grafana Annotation: 복구 시점 기록
curl -X POST http://localhost:3001/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"text":"DB 복구","tags":["recovery"]}'

# 5. DB 복구
docker unpause fairbid-mysql
```

**정량 측정 항목** (핵심: 장애 전파 증명):

| 지표 | 측정 방법 | 증명 목적 |
|------|----------|----------|
| **p95 응답시간 급증** | Baseline p95 vs 장애 중 p95 | 3초 타임아웃으로 수십~수백배 증가 예상 |
| **TPS 급락** | Baseline TPS vs 장애 중 TPS | 블로킹으로 처리량 급감 |
| 에러율 (%) | 5xx 응답 / 전체 요청 × 100 | 커넥션 타임아웃 실패 |
| 불일치 건수 | Redis 입찰 수 - RDB 입찰 수 | (부차적) 일부 유실 발생 |

**예상**:
- ~~"20건 유실"~~ → **"p95 3000ms 이상, TPS 급락"**이 핵심 문제
- DB 장애가 Redis 작업까지 블로킹 (장애 전파)
- 커넥션 풀 고갈로 다른 API에도 영향 가능
- Grafana에서 Baseline → 장애 → 복구 3단계가 Annotation과 함께 명확히 보임

---

### Phase 2: @Async 한계 증명

> **프레이밍**: 앱 강제 종료는 프로덕션에서 일상적으로 발생하는 시나리오의 축소판이다:
> - 배포 시 graceful shutdown 중 미처리 큐
> - OOM kill에 의한 프로세스 종료
> - 오토스케일링 스케일 인 시 인스턴스 종료
>
> 메모리 기반 큐는 이 모든 상황에서 데이터를 유실한다.

#### Baseline 측정 (정상 상태)

```bash
# @Async 전환 후 정상 상태 부하 → 동기 대비 응답시간 개선 확인
k6 run --duration 30s k6/scenarios/bid-async-test.js
```

**목적**: @Async가 응답시간을 개선하는지 확인 (동기 Baseline과 비교)

#### 테스트 2: 앱 강제 종료 시 메모리 큐 유실

```bash
# 1. Grafana Annotation: 앱 종료 시점 기록
curl -X POST http://localhost:3001/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"text":"앱 강제 종료 (docker kill)","tags":["fault-injection"]}'

# 2. 부하 테스트 중 앱 강제 종료
docker kill fairbid-app

# 3. 재시작
docker start fairbid-app

# 4. Grafana Annotation: 앱 복구 시점 기록
curl -X POST http://localhost:3001/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"text":"앱 재시작 완료","tags":["recovery"]}'

# 5. Redis vs RDB 레코드 수 비교
```

**정량 측정 항목**:

| 지표 | 측정 방법 |
|------|----------|
| 유실 건수 | Redis 입찰 수 - RDB 입찰 수 (재시작 후 안정화 이후 측정) |
| 유실률 (%) | 유실 건수 / Redis 입찰 수 × 100 |
| 복구 여부 | 재시작 후 유실 건수가 줄어드는지 (메모리 큐이므로 복구 불가 예상) |
| 영구 불일치 | 재시작 후에도 Redis-RDB 갭이 유지되는지 |

**예상**: 메모리 큐에 있던 메시지 전부 유실, 재시작 후에도 복구 불가 → **영구 불일치**

---

### Phase 3: Message Queue 비교

#### 테스트 3-1: 앱 강제 종료 후 복구

```bash
# 각 MQ별로 동일한 테스트 실행
# 부하 테스트 중 앱 강제 종료 → 재시작 → 미처리 메시지 확인
```

**측정** (Redis Stream / Kafka / RabbitMQ 각각):
- 유실된 메시지 수
- Pending 메시지 재처리 여부
- 복구 시간

**예상**: MQ에 따라 복구 특성 차이

#### 테스트 3-2: DB 다운 시 장애 격리

```bash
# DB 다운 상태에서 입찰 요청
docker pause fairbid-mysql

# 입찰 요청 전송
k6 run scripts/bid-simple.js

# DB 복구 후 RDB 동기화 확인
docker unpause fairbid-mysql
```

**측정**:
- DB 다운 중 HTTP 응답 코드 (200 vs 500)
- 복구 후 RDB 동기화 여부

---

## 5. 구현 계획

### 5.1 브랜치 전략

```
main
├── chore/62-async-sync-test       # Phase 1-2: 동기/@Async 문제점 증명 (#62)
├── chore/63-mq-comparison-test    # Phase 3: MQ 비교 테스트 (#63)
│   ├── (Redis Stream 구현 + 테스트)
│   ├── (Kafka 구현 + 테스트)
│   └── (RabbitMQ 구현 + 테스트)
└── feat/{N}-{선택된MQ}-async-sync  # 최종 선택 MQ 구현 (테스트 후 이슈 생성)
```

### 5.2 구현 순서

```
[Step 1] 관측 인프라 구성 (#62)
├── 커스텀 Micrometer 메트릭 추가 (BidService)
├── Grafana 대시보드 패널 추가 (입찰 메트릭 + 정합성)
├── Grafana Annotation 자동화 스크립트
├── k6 스크립트 작성 (setup()에서 테스트 유저/경매 시드)
└── Redis vs RDB 비교 측정 스크립트

[Step 2] Phase 1 테스트 - 동기 방식 (#62)
├── Baseline 측정 (정상 상태)
├── 테스트 1 실행 (DB 다운 → 불일치 + 응답시간 전파)
└── 결과 문서화 (정량 수치 + Grafana 스크린샷)

[Step 3] @Async 구현 + Phase 2 테스트 (#62)
├── BidService RDB 쓰기 @Async 전환
├── Baseline 측정 (동기 대비 개선 확인)
├── 테스트 2 실행 (앱 강제 종료 → 메모리 큐 유실)
└── 결과 문서화 (정량 수치 + Grafana 스크린샷)

[Step 4] Redis Stream 구현 + 테스트 (#63)
├── Redis Stream Consumer 구현
├── Phase 3 테스트 실행
└── 결과 문서화

[Step 5] Kafka 구현 + 테스트 (#63)
├── Kafka 설정 + Consumer 구현
├── Phase 3 테스트 실행
└── 결과 문서화

[Step 6] RabbitMQ 구현 + 테스트 (#63)
├── RabbitMQ 설정 + Consumer 구현
├── Phase 3 테스트 실행
└── 결과 문서화

[Step 7] 최종 분석 + 결정 (#63)
├── 비교표 작성
├── 트레이드오프 분석
└── 최종 선택 + 근거 문서화
```

---

## 6. 결과 기록 형식

### 6.1 테스트 결과 문서

```markdown
# Phase N 테스트 결과

## 테스트 환경
- 날짜: YYYY-MM-DD
- 환경: Docker Desktop (Windows)
- 스펙: CPU, RAM

## Baseline (정상 상태)
| 지표 | 값 |
|------|---|
| TPS | N req/s |
| p95 응답시간 | Nms |
| 에러율 | 0% |
| Redis-RDB 불일치 | 0건 |

## 테스트 N: {테스트명}

### 시나리오
- 부하: N VUs, M 요청
- 장애 주입: {방법}

### 정량 결과 (핵심: 장애 전파 수치)
| 지표 | Baseline | 장애 중 | 복구 후 | 비고 |
|------|----------|---------|---------|------|
| **p95 응답시간** | Nms | Nms | Nms | 장애 전파의 핵심 지표 |
| **TPS** | N req/s | N req/s | N req/s | 처리량 급락 |
| 에러율 (%) | 0% | X% | 0% | 커넥션 타임아웃 |
| 불일치 건수 | 0건 | N건 | N건 | (부차적) |

### Grafana 스크린샷
{Annotation 포함 시계열 그래프 - Baseline → 장애 → 복구 3단계}

### 분석
{문제점 / 원인 분석 / 정량 수치 해석}
```

### 6.2 최종 비교표

```markdown
# 아키텍처 비교 결과

| 기준 | 동기 | @Async | Redis Stream | Kafka | RabbitMQ |
|------|------|--------|--------------|-------|----------|
| 데이터 유실률 | N% | N% | N% | N% | N% |
| 장애 격리 | ❌ | ✅ | ✅ | ✅ | ✅ |
| 복구 시간 | N/A | ❌ | Nms | Nms | Nms |
| 운영 복잡도 | 낮음 | 낮음 | 낮음 | 높음 | 중간 |

## 최종 선택: {선택}

### 선택 근거
1. ...
2. ...

### 제외 사유
- {옵션}: {사유}
```

---

## 7. 제약사항 및 Trade-offs

### 7.1 의도적 제외

| 제외 항목 | 사유 |
|----------|------|
| 성능 최적화 | 이미 TPS 1,000 처리 가능, 성능은 문제가 아님 |
| 분산 환경 테스트 | 로컬 Docker로 단일 노드만 테스트 |
| 장기 안정성 테스트 | 포폴 목적상 단기 테스트로 충분 |
| Redis 장애 테스트 | Redis는 Source of Truth이므로 장애 시 입찰 자체 불가. HA(고가용성) 영역으로 별도 대응 (Redis Sentinel/Cluster). 참고: `docs/high-availability-SPEC.md` |
| DB 지연 주입 테스트 | 동기 호출의 지연 전파는 자명한 결론. DB 다운 테스트에서 응답시간 변화도 함께 관측 가능 |

### 7.2 학습 비용 고려

- Kafka/RabbitMQ 첫 경험
- 학습 시간이 구현 시간에 포함됨
- 이것 자체가 포트폴리오 가치 (새 기술 습득 과정)

---

## 8. 포트폴리오 스토리라인

### 8.1 전체 흐름

```
[Phase 1: 문제 발견 - 장애 전파]
동기 방식에서 DB 장애 시 전체 API 마비
→ 수치: p95 Nms → Nms (N배 증가), TPS N → N 급락
→ 핵심: "20건 유실"이 아닌 "장애 전파로 서비스 품질 저하"

[Phase 2: 1차 해결 - @Async]
비동기 전환으로 장애 격리 성공
But 앱 재시작 시 메모리 큐 유실
→ 수치: N건 영구 유실, 복구 불가
→ 핵심: "메모리 유실"이 @Async → MQ 전환 근거

[Phase 3: 2차 해결 - Message Queue]
3가지 옵션 비교 (Redis Stream / Kafka / RabbitMQ)
→ 수치: 유실 0건, 재시작 후 N초 내 자동 복구
→ 테스트 데이터 기반 객관적 선택

[결론]
"왜 이 기술을 선택했는가?"에 대한 명확한 답변 가능
오버엔지니어링 없이 문제에 적합한 기술 선택
```

### 8.2 핵심 의사결정: 유실률 0% vs Eventual Consistency

테스트 설계 과정에서 **"유실률 0%가 정말 필요한가?"** 라는 질문이 나왔다.

#### 기존 구조 분석

```
입찰 처리: Redis (Lua 스크립트, 실시간 검증)
현재가 저장: Redis (Source of Truth)
낙찰자 결정: RDB (bidRepository.findTop2ByAuctionId)
              ↑
              불일치!
```

- 입찰 검증과 현재가는 **Redis 기준**
- 낙찰자 결정만 **RDB 기준** → 아키텍처 불일치
- RDB 유실 시 낙찰자 결정 불가 → 유실률 0% 강제

#### 트레이드오프 분석

| 접근법 | 장점 | 단점 |
|--------|------|------|
| 유실률 0% 추구 | 데이터 안정성 | 오버엔지니어링, 복잡한 복구 로직 |
| Eventual Consistency | 아키텍처 단순화, 유연성 | 일시적 불일치 허용 필요 |

#### 결정: Redis를 Single Source of Truth로

```
[변경 전]
Redis(입찰) → RDB(낙찰자) → 불일치 가능 → 0% 필수

[변경 후]
Redis(입찰) → Redis(낙찰자) → 일관성 → Eventual OK
```

**근거**:
1. 이미 입찰의 핵심 로직(검증, 현재가)은 Redis에서 처리
2. 낙찰자만 RDB에서 결정하는 건 아키텍처적 불일치
3. Redis 기준으로 통일하면 RDB는 순수하게 이력 백업 역할
4. "0%를 맹목적으로 추구"하는 대신 **도메인에 맞는 트레이드오프 선택**

**관련 이슈**: [#58 낙찰자 결정 기준을 RDB에서 Redis로 변경](https://github.com/ahn-h-j/Fairbid/issues/58)

#### 포폴에서 어필할 포인트

> "유실률 0%라는 숫자에 집착하지 않고, 도메인 요구사항을 분석해서 적절한 수준의 일관성을 선택했습니다.
> 모든 시스템이 Strong Consistency를 필요로 하지 않으며, 트레이드오프를 이해하고 상황에 맞게 선택하는 것이 중요합니다."

### 8.3 데이터 조회 정책

**"RDB 동기화 지연이 UX에 영향을 주는가?"**에 대한 분석 결과:

#### 화면별 데이터 소스

| 화면 | 데이터 | 소스 | 정합성 요구 |
|------|--------|------|-------------|
| 경매 상세 | 현재가, 내 순위, 1/2순위 여부 | Redis | **실시간 필수** |
| 내 거래 목록 | 입찰 이력, 내 최고 입찰가 | RDB | Eventual OK |
| 마이페이지 | 거래 통계 | RDB | Eventual OK |

#### Redis 저장 구조 (bid.lua)

```
auction:{id} (Hash)
├── currentPrice      # 현재가
├── topBidderId       # 1순위 입찰자 ID
├── topBidAmount      # 1순위 입찰 금액
├── secondBidderId    # 2순위 입찰자 ID
├── secondBidAmount   # 2순위 입찰 금액
└── ...
```

- 상위 2명의 입찰 정보를 Redis에 저장
- 경매 상세 페이지에서 "내가 1순위인지" 실시간 확인 가능

#### 결론

- **실시간 필요한 정보**: Redis에서 조회 → RDB 동기화와 무관
- **이력/통계 정보**: RDB에서 조회 → 수 초~분 지연 허용
- RDB 동기화 지연이 **사용자 UX에 영향 없음**

---

---

## 예상 vs 현실 (테스트 후 작성 예정)

> 이 섹션은 테스트 실행 후 "계획과 달랐던 점"을 기록할 공간입니다.

| 항목 | 예상 | 실제 | 배운 점 |
|------|------|------|---------|
| TBD | - | - | - |

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-01-31 | 1.0 | 스펙 인터뷰 기반 초안 작성 |
| 2026-02-05 | 1.1 | 계획 문서임을 명시, 예상vs현실 섹션 추가 |
| 2026-02-05 | 1.2 | 테스트 1-1 삭제(1-2에 통합), 관측 인프라 섹션 추가, Baseline 측정 추가, 정량 측정 기준 강화, Grafana Annotation 추가, @Async 프레이밍 확장, 테스트 데이터 시드(k6 setup) 명시, 브랜치 전략 이슈 기반으로 변경 |
