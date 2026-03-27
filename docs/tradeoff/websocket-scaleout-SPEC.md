# WebSocket 수평확장 Specification

> **이 문서는 향후 진행할 테스트의 계획서입니다. 아직 실행 전이며, 실행 후 별도 결과 문서를 작성할 예정입니다.**
> 모놀리스에 오토스케일링을 적용했을 때 발생하는 WebSocket 메시지 동기화 문제를 단계별로 해결하고, 최종적으로 REST/WebSocket 서버 분리까지 진화한다.

---

## 1. Overview

### Problem Statement

현재 WebSocket이 Simple Broker(인메모리)를 사용하고 있다.
단일 서버에서는 문제가 없지만, 오토스케일링으로 인스턴스가 늘어나면
서버 간 메시지 동기화가 안 된다.

```
[단일 서버]
서버1: 경매방 구독자 [A, B, C] → 입찰 발생 → A, B, C 다 받음

[오토스케일링 → 2대]
서버1: 경매방 구독자 [A, B] → 입찰 발생 → A, B만 받음
서버2: 경매방 구독자 [C]   → 모름       → C는 못 받음
```

각 서버가 자기 메모리에만 구독자를 들고 있으므로, 다른 서버에 붙은 사용자에게 메시지가 전달되지 않는다.

### Solution Summary

단계별로 문제를 해결하며 최종적으로 REST/WebSocket 서버 분리까지 진화한다.

```
모놀리스 오토스케일링 → 메시지 동기화 실패
  → Sticky Session (임시 해결, 한계 체감)
  → Redis Pub/Sub (Stateless 달성)
  → REST/WS 분리 (독립 스케일링)
```

### Success Criteria

| 기준 | Step 1 (단일) | Step 2 (ASG) | Step 3 (Sticky) | Step 4 (Pub/Sub) | Step 5 (분리) |
|------|-------------|-------------|----------------|-----------------|-------------|
| 메시지 수신율 | 100% | 하락 | 복구 | 100% | 100% |
| 부하 분산 | N/A | 불균형 | 쏠림 발생 | 균등 | 균등 |
| 장애 격리 | N/A | N/A | 서버 다운 시 끊김 | 서버 다운 시 재연결 | REST/WS 독립 |
| 독립 스케일링 | N/A | N/A | N/A | N/A | REST/WS 독립 |

---

## 2. 테스트 환경

- **인프라**: AWS EC2 + ALB + Auto Scaling Group
- **부하 테스트**: k6 (로컬에서 실행, 무료)
- **모니터링**: Grafana + CloudWatch

---

## 3. 테스트 시나리오

### Step 1: 모놀리스 단일 서버 (기준선)

**목표**: 오토스케일링 전 단일 서버 성능 기준선 확보

**구성**:
- EC2 1대 (모놀리스)

**측정 항목**:

| 지표 | 설명 |
|------|------|
| 동시 WebSocket 연결 수 | 최대 유지 가능 연결 |
| REST API 응답 시간 | p50, p95, p99 |
| WebSocket 메시지 수신율 | 100% (기준선) |
| CPU/메모리 사용률 | 부하 구간별 리소스 |

**산출물**: Grafana 대시보드 캡처, 기준선 수치 문서화

---

### Step 2: ASG 오토스케일링 → 문제 재현

**목표**: 오토스케일링 적용 후 WebSocket 메시지 동기화가 안 되는 것을 재현

**구성**:
- ALB + Auto Scaling Group (CPU 기반 스케일링 정책)

**시나리오**:
```
1. k6로 부하 인가
2. CPU 임계치 초과 → ASG가 인스턴스 자동 추가
3. CloudWatch/Grafana에서 인스턴스 증가 확인
4. 사용자 A가 서버1에서 입찰 → 서버2에 붙은 사용자 B가 못 받음
```

**인스턴스 증가 확인 방법**:

| 방법 | 설명 |
|------|------|
| AWS Console | ASG 인스턴스 수 직접 확인 |
| CloudWatch | GroupInServiceInstances 메트릭 |
| Grafana | 인스턴스 수/CPU 그래프 시각화 |
| AWS CLI | `aws autoscaling describe-auto-scaling-groups` |

**정량 측정 항목**:

| 지표 | 측정 방법 | 증명 목적 |
|------|----------|----------|
| 메시지 수신율 하락 | 발행 수 vs 수신 수 비교 | 서버 간 동기화 안 됨 |
| 서버별 구독자 편차 | 서버별 WebSocket 연결 수 | 어떤 서버에 붙느냐에 따라 결과 다름 |

**핵심 질문**: "오토스케일링은 되는데, stateful(WebSocket)이 깨진다"

---

### Step 3: Sticky Session (임시 해결 → 한계 체감)

**목표**: Sticky Session으로 메시지 동기화 문제를 임시 해결하되, 한계를 체감

**구성**:
- ALB Sticky Session 설정 (같은 사용자 → 같은 서버)

**해결되는 것**:
- 같은 경매방 구독자가 같은 서버에 붙으므로 메시지 전달 정상

**한계 재현 시나리오**:

| 문제 | 시나리오 | 확인 방법 |
|------|----------|----------|
| 부하 쏠림 | 인기 경매에 사용자 집중 | 서버별 CPU/메모리 편차 (Grafana) |
| SPOF | 서버 1대 다운 | 해당 서버 경매방 전체 끊김 확인 |
| 스케일링 비효율 | 새 서버 추가 | 기존 세션은 안 옮겨짐 확인 |

**정량 측정 항목**:

| 지표 | Baseline (Step 2) | Sticky 적용 후 |
|------|-------------------|---------------|
| 메시지 수신율 | 하락 | 100% (복구) |
| 서버별 CPU 편차 | - | 쏠림 발생 수치 |
| 서버 다운 시 끊김 수 | - | N개 경매방 전체 끊김 |

**핵심 질문**: "땜빵은 되는데, 근본 해결이 아니다"

---

### Step 4: Redis Pub/Sub (Stateless 달성)

**목표**: Simple Broker를 Redis Pub/Sub으로 교체하여 진정한 Stateless 달성

**구성**:
- Simple Broker → Redis Pub/Sub 메시지 릴레이
- Sticky Session 제거

**동작 원리**:
```
[입찰 발생 - 서버1]
서버1 → Redis Pub/Sub에 메시지 발행
  → 서버1 수신 → 자기 구독자에게 전달
  → 서버2 수신 → 자기 구독자에게 전달
  → 서버N 수신 → 자기 구독자에게 전달
```

**정량 측정 항목**:

| 지표 | Step 3 (Sticky) | Step 4 (Pub/Sub) |
|------|-----------------|------------------|
| 메시지 수신율 | 100% | 100% |
| 서버별 CPU 편차 | 쏠림 | 균등 |
| 서버 다운 시 영향 | 경매방 끊김 | 다른 서버로 재연결 |
| Sticky Session | 필요 | 불필요 |

**핵심 질문**: "이제 어떤 서버에 붙어도 상관없다 = Stateless"

---

### Step 5: REST / WebSocket 서버 분리 (독립 스케일링)

**목표**: 모놀리스에서 발생하는 WebSocket 관련 문제를 재현하고, 서버 분리로 해결

**배경**:

Step 4에서 Redis Pub/Sub으로 서버 간 메시지 동기화를 달성했다(Stateless).
하지만 REST와 WebSocket이 같은 프로세스에서 동작하는 **모놀리스 구조**이기 때문에
세 가지 문제가 발생한다.

```
[문제 1: 배포 시 커넥션 드롭]
REST 코드 수정 → 배포(Instance Refresh) → 구 인스턴스 종료
→ 해당 인스턴스의 WebSocket 커넥션 전부 끊김
→ "무중단 배포"는 REST 기준이지, WebSocket은 무중단이 아님

[문제 2: 스케일아웃 후 WebSocket 커넥션 쏠림]
서버A에 100명 연결 → 스케일아웃 → 서버B 추가
→ REST 요청은 A/B로 분산됨
→ WebSocket 커넥션은 A에 100명 그대로, B에 0명
→ 스케일아웃이 WebSocket에는 무의미

[문제 3: 장애 격리 불가]
REST와 WebSocket이 같은 프로세스
→ 한쪽이 죽으면 다른 쪽도 같이 죽음
→ 분리되어 있으면 한쪽만 죽고 다른 쪽은 살아있어야 함
```

**왜 "새벽에 배포하면 되지 않나?"가 통하지 않는가**:

이 시스템은 24시간/48시간 경매다. 새벽에도 경매가 돌아가고 있고,
종료 직전 5분이 입찰이 가장 치열한 시점이다. 새벽 종료 경매의 마감 입찰 중에 배포하면 최악의 UX 사고가 발생한다.

**왜 "무중단 배포하면 되지 않나?"가 통하지 않는가**:

롤링 배포(Instance Refresh)의 동작:
- 새 인스턴스 띄움 → 헬스체크 통과 → LB에 등록
- 구 인스턴스를 LB에서 제거 (connection draining)
- draining timeout(기본 300초) 후 구 인스턴스 **강제 종료**
- REST는 요청이 짧으니 자연스럽게 빠짐 → **무중단**
- WebSocket은 커넥션이 계속 살아있으니 timeout 후 **강제 끊김**

"무중단 배포"는 **REST 기준 무중단**이지, WebSocket은 무중단이 아니다.

---

#### Step 5-1: 모놀리스 문제 재현

**시나리오 A: 배포 시 WebSocket 커넥션 드롭**

```
사전: ASG 1대, load-test 프로필
  1. k6로 WebSocket 구독자 20명 연결 유지 + 주기적 입찰
  2. 모든 구독자 연결 확인 후 30초 대기
  3. ASG Instance Refresh 트리거 (롤링 배포 시뮬레이션)
  4. 새 인스턴스 뜸 → 구 인스턴스 draining → 종료
  5. 측정: WebSocket 커넥션 끊김 수, 끊기는 시점의 메시지 유실
결과: 구 인스턴스 구독자 전원 끊김
```

| 지표 | 기대 결과 |
|------|----------|
| WebSocket 커넥션 끊김 수 | 구 인스턴스 구독자 전원 (20명) |
| 메시지 수신율 | 배포 중 하락 |

**시나리오 B: 스케일아웃 후 WebSocket 커넥션 쏠림**

```
사전: ASG 1대 (서버A)
  1. k6로 WebSocket 구독자 N명 연결 (전부 서버A에 붙음)
  2. ASG desired=2로 증가 → 서버B 추가
  3. 서버B healthy 확인 후
  4. 각 인스턴스에 /actuator/wsconnections 조회
  5. 측정: 서버A의 커넥션 수, 서버B의 커넥션 수
결과: 서버A=N명, 서버B=0명 (REST는 분산되지만 WS는 안 옮겨감)
```

| 지표 | 기대 결과 |
|------|----------|
| 서버A WebSocket 커넥션 수 | N명 (전원) |
| 서버B WebSocket 커넥션 수 | 0명 |
| REST 요청 분산 | 서버A ~50%, 서버B ~50% |

**핵심**: 스케일아웃이 REST에만 효과 있고, WebSocket에는 무의미하다.
리소스 경합, LB 분산 불균형도 같은 원인에서 발생하는 문제다.

**핵심 질문**: "Stateless는 달성했지만, 모놀리스라서 배포/스케일링이 WebSocket을 고려하지 못한다"

---

#### Step 5-2: REST / WebSocket 서버 분리 → 문제 해결

**목표**: Spring Profile로 REST/WebSocket을 분리 배포하여 Step 5-1의 문제가 해결되는 것을 확인

**구성**:
- 같은 코드베이스, Spring Profile로 분리
  - `--spring.profiles.active=api` → REST Controller만 활성화
  - `--spring.profiles.active=ws` → WebSocket Config, STOMP Handler만 활성화
- Redis Pub/Sub은 양쪽 다 연결 (REST에서 발행, WS에서 구독)
- 각각 독립 ASG 구성
- ALB 라우팅: `/api/**` → REST ASG, `/ws/**` → WS ASG

**시나리오 A 재검증: REST 배포 → WebSocket 유지**

```
  1. k6로 WebSocket 구독자 N명 연결 유지
  2. REST ASG만 Instance Refresh 트리거
  3. WebSocket 커넥션 끊김 수 측정
  4. 기대: 0건 (WebSocket 서버는 건드리지 않았으므로)
```

**시나리오 B 재검증: 스케일아웃 → WS 독립 스케일링**

```
  1. WS ASG만 desired 증가
  2. 새 WS 서버에도 커넥션 분산 확인
  3. REST ASG와 독립적으로 스케일링 동작 확인
```

**시나리오 C: 장애 격리 — 한쪽 kill → 다른 쪽 생존**

```
  1. k6로 WebSocket 구독자 N명 연결 + REST 요청 동시 진행
  2. REST 서버 kill → WebSocket 커넥션 유지 + 메시지 수신 정상 확인
  3. WebSocket 서버 kill → REST API 정상 응답 확인
  4. 기대: 한쪽이 죽어도 다른 쪽은 영향 없음
```

**정량 측정 항목**:

| 지표 | Step 5-1 (모놀리스) | Step 5-2 (분리) |
|------|-------------------|----------------|
| REST 배포 시 WS 끊김 | 구 인스턴스 구독자 전원 끊김 | **0건** |
| 스케일아웃 후 WS 커넥션 분포 | 서버A 전원, 서버B 0명 | 각 WS 서버에 분산 |
| REST kill 시 WS 영향 | 같이 죽음 | **WS 생존** |
| WS kill 시 REST 영향 | 같이 죽음 | **REST 생존** |

**핵심 질문**: "REST의 생명주기와 장애가 WebSocket에 전파되지 않는다 = 진정한 독립"

**추가 이점 (글로 정리, 테스트 불가)**:

| 이점 | 설명 |
|------|------|
| 스케일링 축 분리 | REST는 RPS 기반, WebSocket은 커넥션 수 기반으로 각각 오토스케일링 정책 설정 가능 |
| 리소스 최적화 | REST(CPU 바운드)와 WebSocket(메모리 바운드)에 맞는 인스턴스 타입 선택 가능 |

---

## 4. 비용 예측

### EC2 기준 (서울 리전, On-Demand)

| 구성 | 비용 (시연 30시간 기준) |
|------|----------------------|
| Step 1: t3.small x 1 | ~$0.62 |
| Step 2-3: t3.small x 3 + ALB | ~$2.55 |
| Step 4: t3.small x 3 + ALB + Redis | ~$3.00 |
| Step 5: t3.small x 4 + ALB | ~$3.50 |

### 비용 발생 항목

| 항목 | 과금 |
|------|------|
| k6 실행 (로컬) | 무료 |
| EC2 인스턴스 | 시간당 과금 (켜놓은 시간만큼) |
| ALB (로드밸런서) | 시간당 + 요청 수 과금 |
| 오토스케일링 추가 인스턴스 | 늘어난 만큼 추가 과금 |
| 네트워크 (outbound) | 소량이면 거의 무료 |

> 테스트 끝나면 즉시 종료. 전체 **만원 이내** 예상.

---

## 5. 구현 계획

### 5.1 브랜치 전략

```
main
└── chore/69-websocket-scaleout    # WebSocket 수평확장 전체
```

### 5.2 구현 순서

```
[Step 1] 모놀리스 단일 서버 기준선
├── EC2 1대 모놀리스 배포
├── k6 WebSocket + REST 혼합 부하 스크립트 작성
├── 기준선 성능 지표 수집
└── 결과 문서화 (Grafana 캡처)

[Step 2] ASG 오토스케일링 → 문제 재현
├── ALB + ASG 구성
├── k6 부하 → 인스턴스 자동 증가 확인
├── WebSocket 메시지 동기화 실패 재현
└── 결과 문서화

[Step 3] Sticky Session → 한계 검증
├── ALB Sticky Session 설정
├── 메시지 동기화 복구 확인
├── 부하 쏠림 + SPOF 재현
└── 결과 문서화

[Step 4] Redis Pub/Sub
├── Simple Broker → Redis Pub/Sub 교체
├── Sticky Session 제거
├── N대 메시지 동기화 검증
└── 결과 문서화

[Step 5-1] 모놀리스 문제 재현
├── WebSocket 커넥션 수 조회 엔드포인트 구현 (/actuator/wsconnections)
├── 시나리오 A: k6 WebSocket 유지 + Instance Refresh → 커넥션 드롭 측정
├── 시나리오 B: k6 WebSocket 유지 + ASG 스케일아웃 → 커넥션 쏠림 측정
└── 결과 문서화

[Step 5-2] REST / WebSocket 분리 → 해결 확인
├── Spring Profile 분리 (api / ws)
├── 각각 독립 ASG + ALB 라우팅 구성
├── 시나리오 A 재검증: REST 배포 → WS 끊김 0건 확인
├── 시나리오 B 재검증: WS 독립 스케일링 확인
├── 시나리오 C: 한쪽 kill → 다른 쪽 생존 확인 (장애 격리)
└── 전체 Step 1~5 성능 비교 문서
```

---

## 6. 결과 기록 형식

### 6.1 Step별 기록

```markdown
# Step N: {단계명}

## 테스트 환경
- 날짜: YYYY-MM-DD
- 인스턴스: t3.small x N

## 구성
- docker-compose / ASG / ALB 설정

## 시나리오
- 부하: N VUs
- 장애 주입: {방법}

## 정량 결과
| 지표 | Baseline | 테스트 결과 | 비고 |
|------|----------|-----------|------|
| 메시지 수신율 | 100% | N% | ... |
| p95 응답시간 | Nms | Nms | ... |

## Grafana / CloudWatch 스크린샷

## 분석
- 뭘 했고
- 뭐가 일어났고
- 왜 그런지
```

---

## 7. 포트폴리오 스토리라인

```
[출발]
모놀리스에 오토스케일링 붙여봤다

[문제 발견]
스케일 아웃 되니까 WebSocket 메시지가 다른 서버로 안 감
→ Simple Broker가 인메모리라서 서버 간 공유가 안 됨

[임시 해결]
Sticky Session으로 같은 사용자를 같은 서버에 고정
→ 되긴 하는데 부하 쏠림 + 서버 죽으면 끝

[근본 해결]
Redis Pub/Sub으로 서버 간 메시지 동기화
→ Stateless 달성, 어떤 서버에 붙어도 OK

[최적화]
REST/WebSocket이 한 덩어리로 스케일링되니 비효율
→ 분리해서 독립 스케일링 + 배포 독립 + 장애 격리
```

---

## 8. 면접 예상 질문

| 질문 | 답변 포인트 |
|------|------------|
| 오토스케일링 하니까 뭐가 문제였나요? | WebSocket이 인메모리 Simple Broker라서 서버 간 메시지 동기화 안 됨. 서버A 입찰 → 서버B 구독자 못 받음 |
| Sticky Session 왜 안 썼나요? | 임시로 썼는데 부하 쏠림 + 서버 다운 시 경매방 전체 끊김. 근본 해결 아님 |
| Redis Pub/Sub 선택 이유? | 서버 간 메시지 브로드캐스팅에 적합. 이미 Redis 인프라 있음. Kafka는 이 규모에서 오버엔지니어링 |
| 서버 분리 왜 했나요? | 비용보다 배포 독립성과 장애 격리가 핵심. REST 배포해도 WebSocket 안 끊김 |
| 분리하면 비용 절감 되나요? | 인스턴스 수만 보면 비슷할 수 있지만 사이즈 최적화 가능. 진짜 가치는 운영 안정성 |

---

## Step 2 테스트 결과 (2026-03-23)

### 테스트 환경
- 날짜: 2026-03-23
- 인스턴스: t3.small x 2 (ASG, ALB)
- 테스트 도구: k6 (로컬) + 브라우저 시연

### k6 정량 결과

| 지표 | Single (1대) | Multi (2대) |
|------|-------------|-------------|
| WebSocket 연결 | 20/20 | 20/20 |
| 입찰 성공 | 5/5 (→ 1/1로 수정 후) | 1/1 |
| 메시지 수신율 | **100%** | **50%** |
| 서버 분산 | 1대 (172.18.0.2) | 2대 (172.31.15.244, 172.31.42.190) |

### 브라우저 시연 결과

2대 환경에서 같은 경매(#78)를 브라우저 2개로 열고 입찰:
- **왼쪽 브라우저**: 14,000원 (실시간 업데이트 ✅)
- **오른쪽 브라우저**: 13,000원 (업데이트 안 됨 ❌)

→ 입찰이 처리된 서버에 붙은 브라우저만 WebSocket 메시지 수신

### 추가 발견: 인증(로그인) 문제

**예상하지 못한 문제**: 2대 환경에서 OAuth 로그인이 깨짐
- 로그인 후 경매 등록/입찰 시 "로그인이 필요합니다" 에러 발생
- 원인: JWT 토큰 발급/검증 또는 refresh token 쿠키가 서버 간 공유되지 않음
- WebSocket뿐 아니라 **인증도 stateful 문제의 영향을 받음**
- Step 3 (Sticky Session) 적용 시 같은 유저가 같은 서버로 고정되므로 함께 해결 예상

### 예상 vs 현실

| 항목 | 예상 | 실제 | 배운 점 |
|------|------|------|---------|
| 수신율 하락 | < 100% | 50% (2대 균등 분산) | Simple Broker의 서버 간 격리 확인 |
| 테스트 방식 | 입찰 5회로 측정 | 입찰 1회로 수정 필요 | 입찰 N회면 양쪽 서버에 분산되어 전원 수신 → 1회여야 동기화 실패 증명 가능 |
| 서버 식별 | X-Server-Ip 헤더로 확인 | REST 헤더 ≠ WS 서버 | REST 응답의 서버 IP와 WebSocket이 붙은 서버는 다를 수 있음 |
| 인증 | 문제 없을 것 | 로그인 깨짐 | 오토스케일링은 WebSocket만이 아니라 모든 stateful 요소에 영향 |
| IMDS hop limit | 기본값으로 동작 | Docker 컨테이너에서 메타데이터 접근 실패 | Launch Template에 HttpPutResponseHopLimit=2 설정 필요 |

---

## Step 3 테스트 결과 (2026-03-23)

### 구성
- ALB Sticky Session 활성화 (lb_cookie, 24시간)
- ASG 2대

### k6 결과

| 지표 | Step 2 (Sticky 없음) | Step 3 (Sticky 있음) |
|------|---------------------|---------------------|
| 메시지 수신율 | 50% | **50% (변화 없음)** |

### 예상 vs 현실

| 항목 | 예상 | 실제 | 배운 점 |
|------|------|------|---------|
| 수신율 복구 | 100% | 50% (변화 없음) | Sticky Session은 "같은 유저 → 같은 서버" 보장이지, "같은 경매 구독자 → 같은 서버"가 아님. 다른 유저들은 여전히 다른 서버에 분산되므로 WebSocket 동기화 문제는 해결 안 됨 |
| 로그인 문제 | 해결 | 해결 (예상) | 같은 유저의 REST + WS가 같은 서버로 가므로 인증 세션 일관성 확보 |

### 분석

Sticky Session이 해결하는 것:
- **개인 경험의 일관성**: 내가 입찰하면 내 화면에는 반영됨 (내 REST와 WS가 같은 서버)
- **인증 문제**: 로그인 세션이 서버 간 꼬이지 않음

Sticky Session이 해결하지 못하는 것:
- **서버 간 메시지 동기화**: 서버A 유저가 입찰해도 서버B 유저는 여전히 못 받음
- Simple Broker가 인메모리이므로 서버 간 구독자 목록을 공유하지 않는 근본 문제는 그대로

### 결론

Sticky Session은 WebSocket 동기화 문제의 해결책이 아니라, 인증 등 **세션 일관성 문제의 해결책**이다.
WebSocket 메시지 동기화는 서버 간 메시지 공유 메커니즘(Step 4: Redis Pub/Sub)이 필요하다.

---

## Step 4 테스트 결과 (2026-03-24)

### 구성
- Simple Broker → Redis Pub/Sub으로 교체
- Sticky Session OFF
- ASG 2대

### 구현
- `RedisPubSubBroadcastAdapter`: 입찰/종료 메시지를 Redis 채널에 발행
- `RedisMessageSubscriber`: Redis에서 수신 → 로컬 WebSocket 구독자에게 전달
- `RedisPubSubConfig`: Redis Pub/Sub 리스너 설정
- `WebSocketBroadcastAdapter`: @Component 비활성화 (Pub/Sub으로 교체)

### k6 결과

| 지표 | Step 2 (Simple Broker) | Step 4 (Redis Pub/Sub) |
|------|----------------------|----------------------|
| 메시지 수신율 | 50% | **100%** |
| Sticky Session | 불필요 | 불필요 |
| 서버 분산 | 2대 균등 | 2대 균등 |

### 예상 vs 현실

| 항목 | 예상 | 실제 | 배운 점 |
|------|------|------|---------|
| 수신율 | 100% | **100%** | Redis Pub/Sub으로 서버 간 메시지 동기화 달성 |
| Pub/Sub 구독 연결 | 자동 연결 | 초기에 구독자 0명 | Sentinel 환경에서 RedisMessageListenerContainer 연결이 즉시 안 될 수 있음. Instance Refresh 후 정상 연결 |
| 인증 (로그인) | JWT라 문제 없음 | load-test 프로필이 JWT 비활성화 | LoadTestSecurityConfig가 JWT 필터를 X-User-Id 필터로 대체해서 브라우저 로그인 불가. 스케일아웃 문제가 아닌 프로필 설정 문제 |

### 결론

Redis Pub/Sub 적용으로 **Stateless 달성**. 어떤 서버에 WebSocket이 붙어있든 모든 구독자가 메시지를 수신한다.
Sticky Session 없이도 동작하므로 부하가 균등 분산되고, 서버 추가/제거가 자유롭다.

---

## 트레이드오프: Redis Pub/Sub vs Redis Stream

### 배경

Step 4에서 Simple Broker → Redis Pub/Sub으로 교체하여 서버 간 메시지 동기화를 달성했다.
여기서 "Pub/Sub 대신 Redis Stream을 써야 하는 것 아닌가?"라는 질문이 나온다.
Pub/Sub은 fire-and-forget이라 메시지 유실 가능성이 있고, Stream은 영속화 + 재처리가 가능하기 때문이다.

### 비교

| | Pub/Sub | Stream |
|---|---|---|
| 전달 방식 | 브로드캐스트 (모든 구독자 수신) | Consumer Group (한 컨슈머만 처리) |
| 메시지 영속성 | 없음 (fire-and-forget) | 있음 (영속화, ACK 기반 재처리) |
| 지연 | 거의 없음 | 약간 더 높음 |
| 복잡도 | 낮음 | Consumer Group, ACK, 오프셋 관리 필요 |

### 현재 용도에 Pub/Sub이 맞는 이유

현재 하는 일: 입찰/종료 이벤트 발생 → **모든 서버**에 브로드캐스트 → 각 서버가 자기 WebSocket 구독자에게 전달.
이것은 1:N 브로드캐스트이고, Pub/Sub이 정확히 이 용도다.

Stream의 Consumer Group은 한 메시지를 **한 컨슈머만 처리**하는 구조이므로 브로드캐스트에 맞지 않는다.
Stream으로 브로드캐스트를 구현하려면 서버마다 별도 Consumer Group을 만들어야 하는데,
이는 Pub/Sub을 더 복잡하게 재구현하는 것에 불과하다.

### "메시지 유실이 문제 아닌가?"

Pub/Sub에서 메시지가 유실되려면 **구독 중인 서버가 다운**되어야 한다.
그런데 서버가 다운되면 해당 서버의 WebSocket 클라이언트도 **이미 끊긴 상태**다.

```
서버2 다운
  → 서버2에 붙어있던 WebSocket 클라이언트도 끊김
  → Pub/Sub 메시지가 서버2에 전달 안 되어도, 받을 사람이 이미 없음
  → 클라이언트 재연결 → 살아있는 서버1에 붙음
  → 서버1은 정상 구독 중 → 이후 메시지 정상 수신
```

클라이언트 재연결 사이 짧은 틈에 놓치는 메시지는 Stream으로 바꿔도 동일하게 발생한다.
**WebSocket 클라이언트가 끊겨있으면 Stream이 메시지를 영속화해도 전달할 방법이 없다.**

이 문제는 메시지 브로커 레벨이 아니라 **클라이언트 레벨**에서 해결해야 한다:
- 재연결 시 REST API로 현재 상태(최고가, 입찰 내역) 조회
- 또는 WebSocket 연결 직후 서버가 현재 상태를 push

### 결론

| 용도 | 적합한 기술 | 이유 |
|---|---|---|
| 실시간 브로드캐스트 (입찰 가격, 경매 종료) | **Pub/Sub** | 1:N 브로드캐스트, 낮은 지연, 단순한 구조 |
| 유실 불가 작업 큐 (낙찰 처리, 결제, 알림 발송) | **Stream** | 영속화, ACK 기반 재처리, Consumer Group으로 작업 분배 |

Pub/Sub과 Stream은 경쟁 관계가 아니라 **용도가 다른 도구**다.
브로드캐스트는 Pub/Sub을 유지하고, 영속성이 필요한 비동기 작업 큐에 Stream을 별도로 도입하는 것이 올바른 구조다.

---

## 트레이드오프: REST/WebSocket 서버 분리

### 배경

Step 4에서 Redis Pub/Sub으로 Stateless를 달성했다.
하지만 모놀리스이기 때문에 REST 코드 수정 배포 시 WebSocket 커넥션도 함께 끊긴다.
REST와 WebSocket을 별도 프로세스(인스턴스)로 분리할지에 대한 트레이드오프를 정리한다.

### 분리해야 하는 기술적 근거

| 근거 | 설명 |
|---|---|
| 배포 독립성 | REST 배포 시 WebSocket 커넥션이 끊기지 않음. REST는 배포 빈도가 높고, WebSocket은 안정화 후 거의 변경 없음 |
| 장애 격리 | REST 서버 장애가 WebSocket에 전파되지 않음. 경매 중 실시간 가격 갱신이 보호됨 |
| 스케일링 축 차이 | REST는 RPS 기반, WebSocket은 동시 커넥션 수 기반. 하나의 오토스케일링 정책으로 둘 다 최적화 불가 |
| 리소스 특성 차이 | REST는 CPU 바운드(요청-응답 후 해제), WebSocket은 메모리 바운드(장시간 커넥션 유지). 인스턴스 타입 최적화 가능 |
| 로드밸런서 설정 | REST는 라운드로빈, WebSocket은 sticky session이 유리. 같은 서비스면 LB 설정이 충돌 |

### 비용 트레이드오프

분리하면 인스턴스가 최소 2개(REST 1 + WS 1)이므로 비용이 증가한다.
그러나 경매 시스템에서 **입찰 중 커넥션 끊김 = 돈이 걸린 UX 사고**이고,
REST 핫픽스 하나 배포할 때마다 실시간 경매 참여자 전원이 튕기는 것은 기술적으로 허용할 수 없다.

인스턴스 하나 추가하는 비용보다, 배포마다 WebSocket 재연결 + 상태 동기화 로직의 복잡도가 더 크다.

### 분리 방식

같은 코드베이스에서 Spring Profile로 분리한다:
- `--spring.profiles.active=api` → REST Controller만 활성화
- `--spring.profiles.active=ws` → WebSocket Config, STOMP Handler만 활성화
- Redis Pub/Sub은 양쪽 다 연결 (REST에서 발행, WS에서 구독)

코드 중복 없이 **배포 단위만 분리**하는 구조다.

---

## Step 5-1 테스트 결과: 모놀리스 문제 재현 (2026-03-26)

### 테스트 환경
- 날짜: 2026-03-26
- 인스턴스: t3.small x 1 (ASG), ALB
- 테스트 도구: k6 (로컬, 100 VUs)
- 프로필: sentinel, load-test

### 구현 (테스트 인프라)
- `WebSocketConnectionsEndpoint`: 커스텀 Actuator 엔드포인트 (`/actuator/wsconnections`)
  - EC2 메타데이터 API로 서버 IP 조회 (Docker 내부 IP 문제 회피)
  - `WebSocketSessionTracker`로 활성 커넥션 수 반환
- `ServerInstanceIdFilter`: REST 응답에 `X-Server-Ip` 헤더 추가 (서버 분산 추적)
- k6 STOMP heartbeat 수동 전송 (ALB idle timeout 60초 대응)

### 시나리오 A: 배포 시 WebSocket 커넥션 드롭

```
사전: ASG 1대
  1. k6로 100명 WebSocket 연결 + 15초 간격 입찰
  2. 30초 후 Instance Refresh 트리거
  3. 구 인스턴스 draining → 종료
```

| 지표 | 기대 결과 | 실제 결과 |
|------|----------|----------|
| WebSocket 끊김 수 | 100명 전원 | **100명 전원 (100%)** |
| 끊김 시점 | Instance Refresh 중 | 트리거 후 **~65초** (draining timeout) |
| 커넥션 유지 시간 | - | **95초** (연결 후 ~ 끊김) |
| 끊김 원인 | abnormal closure | `close 1006 (abnormal closure): unexpected EOF` |
| 배포 중 입찰 실패 | 발생 | **10건 연속 실패** (503/504/502) |
| 배포 후 입찰 복구 | 복구 | 새 인스턴스 뜬 후 정상 (서버 IP 변경 확인) |

**핵심**: "무중단 배포"는 REST 기준이지 WebSocket은 무중단이 아니다.
구 인스턴스 종료 시 해당 서버의 **WebSocket 구독자 전원이 강제 끊김**.

### 시나리오 B: 스케일아웃 후 WebSocket 커넥션 쏠림

```
사전: ASG 1대 (서버A)
  1. k6로 100명 WebSocket 연결 (전부 서버A)
  2. ASG desired=2 → 서버B 추가
  3. 서버B ALB healthy 후 60초 대기
  4. /actuator/wsconnections로 서버별 커넥션 수 조회
```

| 지표 | 기대 결과 | 실제 결과 |
|------|----------|----------|
| 서버A WebSocket 커넥션 | 100명 | **100명** |
| 서버B WebSocket 커넥션 | 0명 | **0명** |
| ALB wsconnections 라운드로빈 | A/B 번갈아 | A 5회 / B 5회 (50:50) |
| 서버B ALB healthy 소요 시간 | - | **약 3분** |

**핵심**: 스케일아웃이 REST에만 효과 있고, WebSocket에는 무의미.
기존 커넥션은 새 서버로 이동하지 않으며, 서버B는 커넥션 0명인 채로 리소스만 소비한다.

### 트러블슈팅

| 문제 | 원인 | 해결 |
|------|------|------|
| `/actuator/wsconnections` 500 에러 | ECR 이미지가 엔드포인트 추가 전 버전 | CD workflow_dispatch로 최신 이미지 push |
| serverIp가 Docker 내부 IP (172.18.x.x) | `InetAddress.getLocalHost()`가 컨테이너 IP 반환 | EC2 메타데이터 API (`169.254.169.254`)로 변경 |
| k6 60초만에 종료 | ALB idle timeout(60초)으로 WebSocket 끊김 | STOMP heartbeat 30초 간격 수동 전송 추가 |
| 서버B healthy 전 k6 종료 | DURATION(300초) 시간제한 | 무제한 대기 방식으로 전환 (셸 스크립트가 데이터 수집 후 kill) |
| ASG 인스턴스 MySQL 연결 실패 | fairbid-server 인프라 미기동 | docker-compose-infra.yml 수동 시작 |

### 예상 vs 현실

| 항목 | 예상 | 실제 | 배운 점 |
|------|------|------|---------|
| 시나리오 A 끊김 | 전원 끊김 | **전원 끊김** | Instance Refresh의 draining은 REST 요청 기준. WebSocket은 timeout 후 강제 종료 |
| 시나리오 B 쏠림 | 서버A 전원, 서버B 0명 | **서버A 100명, 서버B 0명** | TCP 커넥션은 서버에 고정. LB는 새 연결만 분산 |
| REST 분산 | 서버B healthy 후 분산 | 시나리오 B에서 wsconnections 조회가 50:50 분산 | ALB 라운드로빈은 새 요청 기준으로 정상 동작 |
| 서버B 기동 시간 | 2~3분 | **약 3분** (InService까지) + 3분 (ALB healthy까지) | EC2 시작 + Docker pull + 앱 부팅 + 헬스체크 통과 시간 고려 필요 |

### 결론

모놀리스 구조에서 REST와 WebSocket이 같은 프로세스에 있으면:
1. **배포 = WebSocket 끊김**: REST 한 줄 고쳐도 WebSocket 구독자 전원 강제 disconnect
2. **스케일아웃 = WebSocket에 무의미**: 새 서버가 추가돼도 기존 커넥션은 이동하지 않음

→ Step 5-2에서 Spring Profile로 REST/WebSocket을 분리하여 이 문제들이 해결되는 것을 검증한다.

---

## Step 5-2 테스트 결과: REST/WebSocket 분리 → 문제 해결 (2026-03-27)

### 테스트 환경
- 날짜: 2026-03-27
- 인스턴스: t3.small x 3 (인프라 1대 + REST ASG 1대 + WS ASG 1대)
- ALB 경로 기반 라우팅: `/ws*` → WS TG, 나머지 → REST TG
- 테스트 도구: k6 (로컬, 100 VUs)
- 인프라 관리: Terraform

### 구현

**서버 역할 분리 (`server.role` 프로퍼티)**
- `@EnabledOnRole` 커스텀 어노테이션 + `ServerRoleCondition`으로 빈 활성화 제어
- `SERVER_ROLE=api`: REST Controller, 스케줄러, Redis Pub/Sub 발행, Stream 컨슈머만 활성화
- `SERVER_ROLE=ws`: WebSocket Config, Redis Pub/Sub 구독만 활성화
- `SERVER_ROLE=all`: 전체 활성화 (로컬 개발용, 기본값)

**인프라 (Terraform)**
- `fairbid-rest-asg` / `fairbid-rest-tg` / `fairbid-rest-lt` — REST 전용 ASG
- `fairbid-websocket-asg` / `fairbid-websocket-tg` / `fairbid-websocket-lt` — WS 전용 ASG
- ALB 리스너 규칙: priority 10, `/ws`, `/ws/*` → WS TG
- CD workflow: 양쪽 ASG 모두 Instance Refresh

### 시나리오 A 재검증: REST 배포 → WebSocket 커넥션 유지

```
사전: REST ASG 1대, WS ASG 1대
  1. k6로 100명 WebSocket 연결 (WS TG 경유)
  2. 30초 후 REST ASG만 Instance Refresh 트리거
  3. WebSocket 커넥션 끊김 수 측정
```

| 지표 | Step 5-1 (모놀리스) | Step 5-2 (분리) |
|------|-------------------|----------------|
| WebSocket 끊김 수 | **100명 (100%)** | **0명 (0%)** |
| REST 배포 중 입찰 실패 | 10건 연속 실패 | 발생 (REST 서버 교체 중이므로 정상) |
| WS 서버 영향 | 같이 죽음 | **영향 없음** |

**핵심**: REST 코드 한 줄 고쳐서 배포해도 WebSocket 구독자는 끊기지 않는다.

### 시나리오 C: 장애 격리 — REST kill → WebSocket 생존

```
사전: REST ASG 1대, WS ASG 1대
  1. k6로 100명 WebSocket 연결 + REST 요청 동시 진행
  2. 30초 후 REST 인스턴스 terminate
  3. WebSocket 커넥션 유지 여부 + REST 복구 확인
```

| 지표 | 결과 |
|------|------|
| WebSocket 끊김 수 | **0명 (0%)** |
| REST kill 전 성공률 | 100.0% |
| REST kill 후 성공률 | 2.2% (서버 없으므로 정상) |
| REST ASG 자동 복구 | InService=1 (자가 치유) |

**핵심**: REST 서버가 죽어도 WebSocket 커넥션은 전원 생존한다. ASG가 REST 인스턴스를 자동 교체한다.

### 시나리오 C: 장애 격리 — WS kill → REST 생존

```
사전: REST ASG 1대, WS ASG 2대 (이전 테스트에서 증가된 상태)
  1. k6로 100명 WebSocket 연결 + REST 요청 동시 진행
  2. 30초 후 WS 인스턴스 1대 terminate
  3. REST 정상 응답 여부 + WS 끊김 범위 확인
```

| 지표 | 결과 |
|------|------|
| REST kill 전 성공률 | 100.0% |
| REST kill 후 성공률 | **100.0%** (WS 죽어도 REST 영향 없음) |
| WS 연결 성공 | 99명 |
| WS 끊김 수 | 50명 (50.5%) — kill된 서버에 붙은 커넥션만 끊김 |
| WS ASG 자동 복구 | InService=2 (자가 치유) |

**핵심**: WS 서버가 죽어도 REST API는 100% 정상 응답한다. WS 끊김은 kill된 서버에 붙은 커넥션만 영향받으며, 나머지 WS 서버의 커넥션은 생존한다.

### Step 5-1 vs Step 5-2 비교 요약

| 지표 | Step 5-1 (모놀리스) | Step 5-2 (분리) |
|------|-------------------|----------------|
| REST 배포 시 WS 끊김 | 100명 전원 끊김 | **0건** |
| REST kill 시 WS 영향 | 같이 죽음 | **WS 전원 생존** |
| WS kill 시 REST 영향 | 같이 죽음 | **REST 100% 정상 응답** |
| 독립 스케일링 | 불가 (같은 ASG) | **REST/WS 각각 독립 ASG** |

### 트러블슈팅

| 문제 | 원인 | 해결 |
|------|------|------|
| k6 WS 60초 후 전원 끊김 | ALB idle timeout 60초, heartbeat 미전송 | k6 스크립트에 STOMP heartbeat 30초 간격 전송 추가 |
| k6 정상 종료도 "끊김"으로 카운트 | `socket.close()` 호출 시 close 이벤트 발생, 정상/비정상 미구분 | `gracefulClose` 플래그로 정상 종료와 비정상 끊김 구분 |
| `/actuator/wsconnections` 0명 반환 | ALB default 규칙으로 REST 서버에 라우팅, REST 서버에서는 WS 빈 비활성화 | WS 인스턴스 private IP로 직접 조회 (인프라 서버 SSH 경유) |
| Terraform apply 시 인프라 서버 교체 시도 | `data.aws_ami.ubuntu`가 최신 AMI를 조회, 기존과 다름 | `lifecycle { ignore_changes = [ami, user_data] }` 추가 |
| ALB SG 교체 시도 | description 불일치 (`forces replacement`) | 테라폼 코드의 description을 기존 AWS 값과 일치시킴 |
| SG 규칙 누락으로 MySQL 연결 실패 | 수동 생성한 SG 규칙이 Terraform state에 없어 적용 안 됨 | state에서 제거 후 `terraform apply`로 재생성 |
| IAM Instance Profile 이름 오류 | `fairbid-ec2-role` → 실제 이름 `fairbid-app-profile` | 실제 이름으로 수정 |

### 결론

REST/WebSocket 서버를 같은 코드베이스에서 `server.role` 프로퍼티로 분리한 결과:
1. **배포 격리**: REST 배포해도 WebSocket 구독자 끊김 0건
2. **장애 격리**: REST 서버 죽어도 WebSocket 전원 생존
3. **독립 스케일링**: REST는 RPS 기반, WebSocket은 커넥션 수 기반으로 각각 오토스케일링 가능

모놀리스 대비 코드 변경은 최소화하면서(어노테이션 태깅만), 인프라 수준에서 완전한 독립성을 달성했다.

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-03-07 | 1.0 | 초안 작성 |
| 2026-03-23 | 1.1 | Step 2 테스트 결과 기록 (k6 50% 수신율, 브라우저 시연, 인증 문제 발견) |
| 2026-03-23 | 1.2 | Step 3 테스트 결과 기록 (Sticky Session은 WebSocket 동기화와 무관, 예상과 다른 결과) |
| 2026-03-24 | 1.3 | Step 4 테스트 결과 기록 (Redis Pub/Sub 100% 수신율, Stateless 달성) |
| 2026-03-25 | 1.4 | 트레이드오프 추가: Pub/Sub vs Stream, REST/WS 서버 분리 |
| 2026-03-25 | 1.5 | Step 5 시나리오 재구성: 배포 끊김(A) + 커넥션 쏠림(B) + 장애 격리(C) |
| 2026-03-26 | 1.6 | Step 5-1 테스트 결과 기록 (모놀리스 문제 재현: 배포 끊김 100%, 커넥션 쏠림 100:0) |
| 2026-03-27 | 1.7 | Step 5-2 테스트 결과 기록 (REST/WS 분리: 배포 끊김 0%, 장애 격리 성공) |
