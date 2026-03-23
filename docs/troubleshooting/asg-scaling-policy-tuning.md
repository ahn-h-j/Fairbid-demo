# ASG 스케일링 정책 튜닝 과정

> CPU 기반 스케일링에서 RequestCount + CPU Step Scaling 조합으로 전환한 과정과 근거

---

## 배경

- 아키텍처: `클라이언트 → ALB → Spring Boot → Redis Lua (입찰 핵심 로직)`
- ASG: t3.small, min=1 / max=4
- 부하 테스트: k6로 ALB에 입찰 부하 전송

---

## 1차 시도: CPU 50% Target Tracking

### 설정
```hcl
predefined_metric_type = "ASGAverageCPUUtilization"
target_value = 50.0
```

### 문제 1: 스케일아웃 반응이 느림

- k6 VU 100으로 부하 시작 후 **약 9분 뒤에야** 첫 스케일아웃 발생
- 원인: 입찰 핵심 로직이 Redis Lua에서 처리됨 (초당 20만 TPS)
- Spring Boot 서버는 Redis에 요청을 넘기는 얇은 계층 → **CPU가 잘 안 올라감**
- CPU가 임계치에 도달하는 시점이 실제 서비스 부하와 괴리

### 문제 2: 과잉 스케일아웃

- 12:59 - 1→2 (CPU AlarmHigh)
- 13:05 - 2→4 (CPU AlarmHigh, 6분 만에 max 도달)
- 2번째 인스턴스가 아직 트래픽을 분산하기 전에 알람이 재평가됨
- CloudWatch 메트릭의 1~2분 reporting lag으로 **이미 해소된 부하에 반응**

---

## 2차 시도: RequestCount + CPU Target Tracking 병행

### 설정
```hcl
# 메인: 요청 수 기반
predefined_metric_type = "ALBRequestCountPerTarget"
target_value = 1000

# 보조: CPU 기반
predefined_metric_type = "ASGAverageCPUUtilization"
target_value = 70.0
```

### 문제: 두 Target Tracking 정책의 시차 발동

- 20:34:42 - CPU 70% 정책 발동 → 1→2
- 20:34:53 - RequestCount 정책 발동 → 2→4 (19초 후)
- 두 정책이 **동시가 아닌 순차적으로** 발동하면서 desired가 누적됨
- Target Tracking은 각각 독립적으로 desired를 계산하기 때문에, 시차 발동 시 과잉 스케일링 발생

### 교훈

> Target Tracking 정책 2개를 동시에 사용하면, "둘 중 큰 쪽을 따른다"는 건 **정확히 동시에 평가될 때**만 해당.
> 실제로는 수초~수십초 차이로 순차 발동되면서 의도치 않게 누적된다.

---

## 3차 시도 (최종): RequestCount Target Tracking + CPU Step Scaling

### 설정

```hcl
# 메인: 요청 수 기반 Target Tracking
resource "aws_autoscaling_policy" "request_count_target_tracking" {
  policy_type               = "TargetTrackingScaling"
  estimated_instance_warmup = 120  # 2분

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${alb_arn_suffix}/${tg_arn_suffix}"
    }
    target_value = 10000  # 인스턴스당 1분간 요청 10,000개
  }
}

# 보조: CPU Step Scaling (비상 안전망)
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2       # 2분 연속
  period              = 60
  threshold           = 80      # CPU 80%
}

resource "aws_autoscaling_policy" "cpu_step_scaling" {
  policy_type     = "StepScaling"
  adjustment_type = "ChangeInCapacity"

  step_adjustment {
    scaling_adjustment          = 1   # +1대만
    metric_interval_lower_bound = 0
  }
}
```

### 왜 이 조합인가

| 구분 | 메인 (RequestCount) | 보조 (CPU Step) |
|------|---------------------|-----------------|
| 방식 | Target Tracking | Step Scaling |
| 기준 | 인스턴스당 요청 10,000/분 | CPU 80% 초과 |
| 동작 | desired를 계산하여 설정 | 현재 desired에서 +1 |
| 역할 | 트래픽 비례 확장 | 비정상 CPU 급등 대비 |
| 충돌 시 | desired를 직접 계산 | +1만 추가 (과잉 최소화) |

**Step Scaling을 보조로 쓰는 이유:**
- Target Tracking은 "desired = N"으로 설정 → 두 정책이 각각 계산하면 누적
- Step Scaling은 "+1" → 기존 desired에 상대적으로 동작 → 최악의 경우에도 1대만 추가

---

## target_value 수치 결정 과정

### 왜 CPU가 아닌 RequestCount인가

FairBid의 입찰 핵심 로직은 Redis Lua 스크립트로 처리된다 (초당 ~20만 TPS).
Spring Boot 서버는 요청을 받아 Redis에 넘기는 얇은 계층이라 **CPU가 실제 부하를 반영하지 못한다.**

```
클라이언트 → ALB → Spring Boot (CPU 낮음) → Redis Lua (여기서 다 처리)
```

CPU 50% 기준으로 테스트했을 때:
- VU 100 부하 시작 후 **9분이 지나서야** 첫 스케일아웃 발생
- 서버가 실제로 바쁜 건 맞지만 CPU 지표에 반영이 안 됨
- → **트래픽 양(RequestCount)이 이 아키텍처에 맞는 스케일링 시그널**

### ALBRequestCountPerTarget 동작 방식

- `target_value = N` → **인스턴스당 1분간 요청 N개**를 목표로 유지
- 전체 요청이 N을 초과하면 인스턴스를 추가하여 인스턴스당 요청을 N 이하로 분산
- 계산: `필요 인스턴스 = 총 분당 요청 / target_value`

### target_value = 1,000 → 왜 실패했는가

처음에 "인스턴스당 분당 1,000 요청"으로 설정했다.

k6 VU별 요청량 계산:
- VU 1명 = 요청 3개/회 (경매 상세 + 목록 + 입찰) × 초당 ~2회 = **초당 ~6 RPS**
- VU 100 = 초당 ~600 RPS = **분당 ~36,000 요청**
- VU 1,000 = 초당 ~6,000 RPS = **분당 ~360,000 요청**

target 1,000일 때 필요 인스턴스:

| VU | 분당 요청 | target 1,000 기준 | 결과 |
|----|-----------|-------------------|------|
| 100 | ~36,000 | 36대 필요 | max=4로 제한 → 4대 |
| 300 | ~108,000 | 108대 필요 | max=4로 제한 → 4대 |
| 1,000 | ~360,000 | **360대 필요** | max=4로 제한 → 4대 |

**⚠️ max 제한이 없었다면 360대가 생성되어 AWS 요금 폭탄을 맞을 뻔했다.**

문제점:
- target이 너무 낮으면 **어떤 부하에서든 항상 max까지 확장**됨
- 스케일링의 "단계적 확장"을 관찰할 수 없음
- 실제 인스턴스 처리 능력보다 훨씬 보수적인 수치

### target_value = 3,000 → 왜 부적절한가

| VU | 분당 요청 | target 3,000 기준 | 결과 |
|----|-----------|-------------------|------|
| 100 | ~36,000 | 12대 필요 | max=4 → 항상 4대 |
| 300 | ~108,000 | 36대 필요 | max=4 → 항상 4대 |

여전히 **항상 max까지 확장**된다. 부하 테스트에서 단계적 확장을 관찰하려면 더 높은 값이 필요.

### target_value = 10,000 → 최종 선택

| VU | 분당 요청 | target 10,000 기준 | 결과 |
|----|-----------|-------------------|------|
| 100 | ~36,000 | 3.6 → **4대** | max 근처, 적절한 확장 |
| 50 | ~18,000 | 1.8 → **2대** | 소규모 부하에선 2대만 |
| 30 | ~10,800 | 1.08 → **1대** | 경미한 부하는 확장 없음 |

선택 근거:
- t3.small이 **분당 10,000 요청은 안정적으로 처리 가능** (초당 ~167 RPS)
  - 서버는 Redis에 위임하는 얇은 계층이므로 이 정도는 충분
- 부하 수준에 따라 **1대 → 2대 → 4대 단계적 확장** 관찰 가능
- 실수로 과도한 VU를 설정해도 max=4가 방어

### estimated_instance_warmup = 120초

- 새 인스턴스가 뜰 때: AWS CLI 설치 → git pull → Docker 빌드 → Spring Boot 시작 → Health Check
- 이 과정에서 CPU가 일시적으로 높아짐 (JVM 워밍업, Docker 빌드 등)
- warmup 동안 해당 인스턴스의 메트릭을 **평균 계산에서 제외**
- 120초보다 짧으면 부팅 중 CPU 급등을 부하로 오인 → 과잉 스케일링 재발

### CPU Step Scaling 80% 보조 정책

- RequestCount가 메인이지만, **요청은 적은데 CPU만 높은** 비정상 상황 대비
  - 예: GC 폭주, 메모리 누수, Redis 연결 장애로 타임아웃 대기
- Target Tracking이 아닌 **Step Scaling**인 이유:
  - Target Tracking 2개 병행 시 시차 발동으로 desired 누적 (2차 시도에서 학습)
  - Step Scaling은 "+1"이라 기존 desired에 상대적 → 최악의 경우에도 1대만 추가
- 임계치 80%: 평소에는 안 걸리고 진짜 비정상 상황에서만 작동

---

## k6 테스트 시나리오

### 변경 전: constant-vus (VU 100, 10분)
```javascript
// 문제: VU 100으로는 CPU가 잘 안 올라가서 스케일링 반응이 9분이나 걸림
executor: 'constant-vus',
vus: 100,
duration: '10m',
```

### 변경 후: constant-vus (VU 80, 20분)
```javascript
// VU 80: t3.small 버스트 크레딧 소진 없이 스케일링 테스트 가능
// 20분: 5분 스케일아웃 + 15분 안정화 관찰
executor: 'constant-vus',
vus: 80,
duration: '20m',
```

왜 VU 80인가:
- VU 150/300은 t3.small의 **버스트 크레딧을 소진**시켜 스케일아웃 후에도 CPU 98% 고정
- VU 80이면 스케일아웃 후 인스턴스당 VU 20~40 → CPU 30~40%로 안정화
- 분당 ~9,600 요청 → target 5,000 기준 2대 → 스케일아웃 발동

왜 20분인가:
- 스케일아웃 파이프라인: CloudWatch 알람 평가 3분 + 인스턴스 부팅 ~2분 = **최소 5분**
- 5분까지: 1대로 버팀 (스케일아웃 전 구간)
- 5~7분: 스케일아웃 발동, 인스턴스 추가
- 7~20분: **확장된 상태에서 부하를 안정적으로 소화하는 모습** 확인 (약 13분)

---

## 예상 그래프

### 인스턴스 수 (GroupInServiceInstances)
```
인스턴스
4 |              ┌──────────────────────────
3 |              │
2 |              │
1 |──────────────┘
  └──────────────────────────────────────────── 시간
  0m        5m              10m          15m
  부하 시작   스케일아웃 발동    안정화 구간    종료
```

### ALB RequestCountPerTarget (인스턴스당)
```
요청/분
36K |──────────┐
            │
10K |- - - - │- - - - - - - - ← target (10,000)
 9K |        └──────────────────────────
  0 |
  └──────────────────────────────────────────── 시간
  0m        5m              10m          15m
  1대가 전부 받음  4대로 분산 (인스턴스당 ~9,000)
```

### CPU Utilization (평균)
```
CPU%
80 |    ┌────┐
60 |    │    │
40 |────┘    └──────────────────────────
20 |
  └──────────────────────────────────────────── 시간
  0m        5m              10m          15m
  1대에 집중     4대로 분산 → CPU 하락
```

---

## t3 버스트 크레딧 소진 이슈

### 문제

VU 150으로 20분 테스트 시, 스케일아웃으로 4대까지 확장했는데도 **모든 인스턴스의 CPU가 98%에서 내려가지 않는** 현상 발생.

### 원인: t3 버스트 인스턴스의 CPU 크레딧 소진

t3.small은 **버스트형 인스턴스**다:
- 베이스라인 CPU: **20%** (2 vCPU 중 실질 0.4 vCPU)
- 평소에 크레딧을 쌓아두고, 부하가 올 때 크레딧을 소비하여 100% 성능 발휘
- **크레딧이 0이 되면 베이스라인(20%)으로 성능 제한**

실제 크레딧 소진 로그:

| 시간 | CPU 크레딧 잔량 | 상태 |
|------|----------------|------|
| 11:09 | 467 | 정상 |
| 11:14 | 469 | 정상 |
| 11:29 | 149 | 급락 |
| 11:34 | **0** | **소진 → 성능 제한** |

크레딧 0 이후:
- 인스턴스는 베이스라인 20%로 제한되지만, 요청은 계속 들어옴
- 제한된 CPU로 요청을 처리하느라 **CPU 98%에서 고정**
- 4대 모두 크레딧 소진 → 스케일아웃의 의미가 사라짐

### t3 버스트 인스턴스가 적합한 경우

FairBid 실제 운영 트래픽 패턴:
- 경매 24/48시간 동안 **간헐적** 입찰
- 종료 직전 **순간적** 트래픽 급증 → 크레딧으로 커버
- 평소에 크레딧을 쌓아두고, 피크 때 소비하는 구조

**t3가 부적합한 것이 아니라, k6로 20분 연속 풀 부하는 실제 트래픽 패턴이 아닌 것.**

### 대응 방안

| 방안 | 설명 | 적합한 경우 |
|------|------|-------------|
| VU 감소 | 크레딧 소진 안 되는 수준으로 테스트 | 부하 테스트 (현재 선택) |
| t3.unlimited | 크레딧 소진 후에도 풀 성능, 추가 과금 | 운영 환경 |
| 비버스트 인스턴스 | c5.large 등 고정 CPU | 지속적 고부하 워크로드 |

### k6 VU 적정 수치 계산

목표: 스케일아웃 전(1대)에 크레딧으로 버티고, 스케일아웃 후(4대)에 안정화

- 1대에서 5분간 버텨야 함 (스케일링 파이프라인 소요 시간)
- 초기 크레딧 ~470, 5분 소진량을 계산하면 VU 80이 적절
- VU 80 = 분당 ~9,600 요청 → target 5,000 기준 2대 필요
- 스케일아웃 후 4대: 인스턴스당 VU 20 → CPU 30~40% → 크레딧 소진 없이 안정

---

## 정책 변경 이력

| 버전 | 메인 정책 | 보조 정책 | 결과 |
|------|-----------|-----------|------|
| v1 | CPU 50% Target Tracking | 없음 | 반응 느림, 과잉 스케일아웃 |
| v2 | RequestCount 1,000 | CPU 70% Target Tracking | 시차 발동으로 과잉 스케일링 |
| v3 | RequestCount 10,000 | CPU 80% Step Scaling (+1) | 스케일아웃 반응 느림 (9분) |
| v4 | RequestCount 5,000 | CPU 80% Step Scaling (+1) | **최종 채택** |

---

## 교훈

1. **스케일링 메트릭은 아키텍처에 맞게 선택** — 핵심 로직이 외부(Redis)에 있으면 CPU는 실제 부하를 반영 못함
2. **Target Tracking 2개 병행은 위험** — 시차 발동으로 desired가 누적, Step Scaling 보조가 안전
3. **max 제한은 필수** — target_value 실수 시 인스턴스 폭증 방지 (360대 생성 방지 사례)
4. **estimated_instance_warmup 설정** — 새 인스턴스 부팅 시간 동안 메트릭 제외하여 과잉 스케일링 방지
5. **t3 버스트 크레딧 고려** — 지속 부하 테스트 시 크레딧 소진으로 성능 제한, 실제 트래픽 패턴과 테스트 부하의 차이를 이해해야 함
