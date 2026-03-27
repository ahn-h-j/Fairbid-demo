# WebSocket 스케일아웃 배경지식 가이드

> 이 문서는 FairBid의 WebSocket 수평확장 작업을 진행하면서 필요한 배경지식을 정리한 것이다.
> AWS 인프라, 네트워킹, Docker, Redis, 스케일링 전략 등 핵심 개념을 다루며,
> 실제 작업 과정에서 겪은 삽질과 교훈도 포함한다.

---

## 목차

1. [전체 아키텍처 개요](#1-전체-아키텍처-개요)
2. [ALB (Application Load Balancer)](#2-alb-application-load-balancer)
3. [ASG (Auto Scaling Group)](#3-asg-auto-scaling-group)
4. [Launch Template & User Data](#4-launch-template--user-data)
5. [Security Group (보안그룹)](#5-security-group-보안그룹)
6. [Docker Compose 인프라/App 분리](#6-docker-compose-인프라app-분리)
7. [Redis Sentinel & announce-ip](#7-redis-sentinel--announce-ip)
8. [ASG 스케일링 정책](#8-asg-스케일링-정책)
9. [t3 버스트 인스턴스](#9-t3-버스트-인스턴스)
10. [ECR (Elastic Container Registry) & CD 파이프라인](#10-ecr-elastic-container-registry--cd-파이프라인)
11. [SSM Parameter Store](#11-ssm-parameter-store)
12. [WebSocket Simple Broker의 한계](#12-websocket-simple-broker의-한계)
13. [Sticky Session](#13-sticky-session)
14. [Redis Pub/Sub을 이용한 메시지 동기화](#14-redis-pubsub을-이용한-메시지-동기화)
15. [k6 부하 테스트](#15-k6-부하-테스트)
16. [삽질 모음 & 교훈](#16-삽질-모음--교훈)
17. [REST/WebSocket 서버 분리](#17-restwebsocket-서버-분리)
18. [Terraform으로 인프라 관리](#18-terraform으로-인프라-관리)
19. [ALB 경로 기반 라우팅](#19-alb-경로-기반-라우팅)
20. [Spring 빈 조건부 활성화](#20-spring-빈-조건부-활성화)

---

## 1. 전체 아키텍처 개요

### 현재 구성 (Step 2 진행 중)

```
[클라이언트 (브라우저 / k6)]
        │
        ▼
[ALB: fairbid-alb]  ← HTTP 80
        │
        ▼
[Target Group: fairbid-app-tg]  ← 헬스체크 /actuator/health
        │
        ├── [App EC2 #1] ── Backend (8080) ─┐
        │                                    │
        └── (ASG가 추가) [App EC2 #2~#4] ───┤
                                             │
                                             ▼
                               [인프라 EC2: 172.31.34.73 (고정)]
                                 ├── MySQL (3306)
                                 ├── Redis Master (6379)
                                 ├── Redis Slave ×2 (6380, 6381)
                                 ├── Sentinel ×3 (26379, 26380, 26381)
                                 ├── Prometheus (9595)
                                 └── Grafana (3001)
```

### 핵심 설계 원칙

- **인프라(DB/Redis/모니터링)는 고정 1대**, App 서버만 오토스케일링
- App 서버는 Stateless → 어떤 인스턴스가 요청 받아도 동일 결과 (목표)
- 인프라 서버의 private IP(`172.31.34.73`)를 SSM Parameter Store로 중앙 관리

### 스케일아웃 5단계 진화 로드맵

```
Step 1: 모놀리스 단일 서버 (기준선 확보)
  ↓
Step 2: ASG 오토스케일링 → WebSocket 동기화 실패 재현    ← 현재 여기
  ↓
Step 3: Sticky Session → 임시 해결 + 한계 체감
  ↓
Step 4: Redis Pub/Sub → Stateless 달성
  ↓
Step 5: REST/WebSocket 서버 분리 → 독립 스케일링
```

---

## 2. ALB (Application Load Balancer)

### 개념

- L7(HTTP/HTTPS) 레벨에서 동작하는 로드밸런서
- 클라이언트는 ALB의 DNS 주소로 요청 → ALB가 뒤에 있는 서버들에 분배
- WebSocket 프로토콜도 지원 (HTTP Upgrade 처리)

### 구성 요소

| 개념 | 설명 |
|------|------|
| **Listener** | ALB가 어떤 포트/프로토콜로 요청을 받을지 정의 (예: HTTP 80) |
| **Target Group** | 요청을 받을 서버(타겟)들의 그룹 |
| **Health Check** | 서버가 살아있는지 주기적 확인 (`/actuator/health`) |
| **Routing Rule** | 경로, 호스트 등 조건에 따라 다른 Target Group으로 분배 가능 |

### ALB vs NLB

| 구분 | ALB (Application) | NLB (Network) |
|------|-------------------|----------------|
| 계층 | L7 (HTTP) | L4 (TCP/UDP) |
| WebSocket | 지원 (HTTP Upgrade) | 지원 (TCP passthrough) |
| 라우팅 | 경로/호스트 기반 가능 | 불가 |
| Sticky Session | 쿠키 기반 지원 | 불가 |
| 속도 | NLB보다 느림 (패킷 검사) | 매우 빠름 |
| 우리 선택 | ✅ HTTP 라우팅 + Sticky Session 필요 | - |

### 주의사항

- ALB는 **최소 2개 AZ(가용 영역)의 서브넷**이 필요하다
- ALB 자체에도 **보안그룹**이 필요하다 (인바운드 80/443)
- Target Group의 헬스체크가 실패하면 ALB가 해당 서버로 트래픽을 안 보낸다
- ALB 시간당 과금 + 요청 수 과금 (LCU 단위)

---

## 3. ASG (Auto Scaling Group)

### 개념

- EC2 인스턴스를 **자동으로 늘리고 줄이는** AWS 서비스
- CPU, 요청 수 등 메트릭 기반으로 스케일링 정책 설정
- 새 인스턴스를 만들 때 Launch Template을 참조

### 구성 요소

| 개념 | 설명 |
|------|------|
| **Launch Template** | 인스턴스 생성 템플릿 (AMI, 인스턴스 타입, 보안그룹, User Data) |
| **Min / Max / Desired** | 최소/최대/목표 인스턴스 수 (우리: 1/4/1) |
| **Scaling Policy** | 스케일 아웃/인 조건 |
| **Health Check Grace Period** | 인스턴스 시작 후 N초 동안 헬스체크 면제 (앱 부팅 시간 고려) |
| **Instance Refresh** | 기존 인스턴스를 새 Launch Template 버전으로 롤링 교체 |

### 동작 흐름

```
CPU 또는 요청 수 임계치 초과
  → CloudWatch 알람 발생 (1~3분)
  → ASG가 Launch Template으로 새 인스턴스 생성
  → User Data 스크립트 실행 (앱 부팅)
  → Target Group에 자동 등록
  → ALB 헬스체크 통과 → 트래픽 수신 시작
```

총 소요 시간: **약 3~5분** (알람 평가 + 인스턴스 부팅 + 헬스체크)

### 자가 치유 (Self-healing)

- 헬스체크 실패 → ASG가 해당 인스턴스 자동 terminate → 새 인스턴스 생성
- 수동 개입 없이 항상 Desired 수를 유지하려고 함

### AMI (Amazon Machine Image)

- 서버 이미지 스냅샷. 현재 서버 상태를 그대로 복제할 수 있음
- 우리 AMI에 포함: Ubuntu 24.04 + Docker + Git + 프로젝트 코드 + .env
- ASG가 새 인스턴스를 만들 때 이 AMI를 기반으로 생성

---

## 4. Launch Template & User Data

### Launch Template

- ASG가 "어떤 스펙의 인스턴스를 만들 것인가"를 정의하는 템플릿
- AMI, 인스턴스 타입, 키페어, 보안그룹, IAM 프로필, User Data 포함
- **버전 관리** 가능 (v1 → v2 → ... → v10)

### User Data

- EC2 인스턴스가 **처음 시작될 때 자동 실행**되는 bash 스크립트
- root 권한으로 실행되지만 cloud-init 환경이라 일반 셸과 다름
- Launch Template에 **base64 인코딩**하여 전달

### 현재 User Data 흐름 (v10, 최종)

```
1. AWS CLI v2 설치 (없으면)
2. git safe directory 설정
3. 기존 Docker 컨테이너 정리
4. git pull origin main (최신 코드/설정)
5. SSM Parameter Store에서 INFRA_HOST 조회
6. .env에 INFRA_HOST 주입 + load-test 프로필 추가
7. ECR 로그인 + 이미지 pull
8. docker compose -f docker-compose-app.yml up -d
```

### 디버깅 방법

- SSH 접속 후 `/var/log/cloud-init-output.log` 확인
- User Data는 **인스턴스 최초 시작 시에만** 실행 (재부팅 시 안 됨)

### Launch Template 버전 이력 (삽질 기록)

| 버전 | 변경 | 결과 |
|------|------|------|
| v4 | 수동 생성 (기본) | 정상 (수동 .env 세팅한 상태) |
| v5 | load-test 프로필 추가 | 실패 (`${infra_private_ip}` Terraform 변수 미치환) |
| v6 | INFRA_HOST 하드코딩 | 폐기 (유지보수 부적합) |
| v7 | SSM Parameter Store 사용 | 실패 (AWS CLI 미설치) |
| v8 | AWS CLI v1 설치 + SSM | 정상 (소스 빌드 방식) |
| v9 | CD 파이프라인 연동 | 정상 (소스 빌드 방식) |
| v10 | **ECR pull + AWS CLI v2** | **정상 (현재)** |

---

## 5. Security Group (보안그룹)

### 개념

- EC2/ALB의 **가상 방화벽**
- 인바운드(들어오는)와 아웃바운드(나가는) 트래픽 규칙 정의
- **Stateful**: 인바운드 허용하면 응답 아웃바운드는 자동 허용

### 현재 구성

```
[fairbid-alb-sg] — ALB용
├── 인바운드: 80 (HTTP) ← 0.0.0.0/0 (전세계)
└── 인바운드: 8080 ← 0.0.0.0/0

[fairbid-sg] — EC2용
├── 인바운드: 22 (SSH) ← 0.0.0.0/0
├── 인바운드: 80 ← 0.0.0.0/0
├── 인바운드: 8080 ← ALB 보안그룹 (sg-xxx)     ← Source에 SG ID
├── 인바운드: 3306 (MySQL) ← 172.31.0.0/16       ← VPC 내부만
├── 인바운드: 6379~6381 (Redis) ← 172.31.0.0/16
└── 인바운드: 26379~26381 (Sentinel) ← 172.31.0.0/16
```

### 핵심 포인트

- **Source에 보안그룹 ID를 지정** → "ALB에서 오는 트래픽만 허용" 가능
- **VPC CIDR(172.31.0.0/16)** → 같은 VPC 내 인스턴스 간만 통신 허용
- 포트를 안 열면 ALB 헬스체크도 실패 → 서비스 장애로 이어짐

---

## 6. Docker Compose 인프라/App 분리

### 왜 분리하는가

오토스케일링은 **App 서버만** 늘려야 한다. DB/Redis가 App과 같은 docker-compose에 있으면 서버마다 별도 DB가 생겨서 데이터가 불일치한다.

### 분리 구조

```
[인프라 EC2 — 고정 1대]
docker-compose-infra.yml
├── MySQL (3306)
├── Redis Master (6379)
├── Redis Slave ×2 (6380, 6381)
├── Sentinel ×3 (26379, 26380, 26381)
├── Prometheus (9595)
└── Grafana (3001)

[App EC2 — ASG로 자동 확장]
docker-compose-app.yml
└── Backend (8080)
    ├── DB → INFRA_HOST:3306
    ├── Redis → INFRA_HOST:6379
    └── Sentinel → INFRA_HOST:26379, 26380, 26381
```

### 핵심 환경변수

| 변수 | 값 | 용도 |
|------|---|------|
| `INFRA_HOST` | 172.31.34.73 | 인프라 서버 private IP |
| `REDIS_SENTINEL_NODES` | ${INFRA_HOST}:26379,... | Sentinel 노드 주소 |
| `SPRING_DATASOURCE_URL` | jdbc:mysql://${INFRA_HOST}:3306/fairbid | DB 연결 URL |

### 주의사항

- Docker 서비스명(`mysql`, `redis`)은 **같은 docker-compose 네트워크 내에서만** 사용 가능
- 외부 서버에서는 **EC2 private IP + 호스트에 매핑된 포트** 사용
- private IP는 **같은 VPC 내에서만** 접근 가능

---

## 7. Redis Sentinel & announce-ip

### Redis Sentinel 구성

Sentinel은 Redis Master를 감시하다가 장애 시 자동으로 Slave를 Master로 승격시킨다.

```
Sentinel ×3 (과반 투표: quorum=2)
  │ 감시
  ▼
Master (6379) ←── 복제 ──→ Slave-1 (6380)
                           Slave-2 (6381)
```

| 설정 | 값 | 의미 |
|------|---|------|
| `sentinel monitor mymaster` | INFRA_HOST 6379 2 | Master 주소, quorum 2 |
| `down-after-milliseconds` | 5000 | 5초 응답 없으면 장애로 판단 |
| `failover-timeout` | 10000 | failover 최대 10초 |
| `min-replicas-to-write` | 1 | Slave 연결 없으면 쓰기 거부 (Split Brain 방지) |
| `min-replicas-max-lag` | 10 | 복제 지연 10초 이내인 Slave만 카운트 |

### announce-ip 문제와 해결

**문제**: Sentinel이 Master 주소를 Docker 내부 IP(172.22.0.10)로 알려줌 → 외부 App 서버에서 접근 불가

```
[문제 상황]
Sentinel → "Master는 172.22.0.10:6379"
→ 외부 App 서버 → Docker 내부 IP에 접근 불가!

[해결: announce-ip]
Sentinel → "Master는 172.31.34.73:6379"
→ 외부 App 서버 → EC2 IP로 접근 가능!
```

**설정 요약**:

| 대상 | 설정 | 용도 |
|------|------|------|
| Redis Master | `--replica-announce-ip ${INFRA_HOST}` | 외부에 EC2 IP 알림 |
| Redis Slave | `--replica-announce-port 6380/6381` | Docker 매핑 포트 알림 |
| Sentinel | `sentinel announce-ip ${INFRA_HOST}` | Sentinel 자신의 외부 IP 알림 |

### Sentinel vs Cluster

| 기준 | Sentinel | Cluster |
|------|----------|---------|
| 목적 | HA (자동 failover) | HA + 수평 확장 (샤딩) |
| 데이터 규모 | 단일 노드 충분 (수 GB 이하) | 수십 GB 이상 |
| Lua 스크립트 | 제약 없음 | 같은 슬롯 키 제약 |
| 운영 복잡도 | 낮음 | 높음 (슬롯 관리, 리밸런싱) |

FairBid은 데이터 < 수 GB + bid.lua 핵심 사용 → **Sentinel이 적정 기술**

### Spring Boot Lettuce 클라이언트 설정

```java
ReadFrom.MASTER          // 모든 읽기/쓰기를 Master에서 (stale 데이터 방지)
autoReconnect: true      // failover 후 새 Master 자동 재연결
REJECT_COMMANDS          // 연결 끊기면 즉시 에러 (대기하지 않음)
fixedTimeout: 3초        // 커넥션/커맨드 타임아웃
```

읽기 분산(`ReadFrom.REPLICA_PREFERRED`)은 비동기 복제 지연으로 인한 stale 데이터 위험 때문에 미적용.

---

## 8. ASG 스케일링 정책

### 왜 CPU가 아닌 RequestCount인가

FairBid의 입찰 핵심 로직은 **Redis Lua 스크립트**에서 처리된다 (초당 ~20만 TPS).
Spring Boot 서버는 Redis에 요청을 넘기는 **얇은 계층**이라 CPU가 실제 부하를 반영 못한다.

```
클라이언트 → ALB → Spring Boot (CPU 낮음) → Redis Lua (여기서 다 처리)
```

CPU 50% 기준 테스트 시 VU 100 부하에서 **9분 뒤에야** 첫 스케일아웃 발생 → 부적절

### 현재 정책 (최종)

| 구분 | 메인: RequestCount Target Tracking | 보조: CPU Step Scaling |
|------|-----------------------------------|----------------------|
| 기준 | 인스턴스당 분당 5,000 요청 | CPU 80% 초과 (2분 연속) |
| 동작 | desired를 계산하여 설정 | 현재 desired에서 +1 |
| 역할 | 트래픽 비례 확장 | 비정상 CPU 급등 대비 (GC 폭주, 메모리 누수 등) |

### 왜 Target Tracking 2개 병행이 위험한가

```
20:34:42 - CPU 70% 정책 발동 → 1→2
20:34:53 - RequestCount 정책 발동 → 2→4 (19초 후)
```

Target Tracking은 각각 독립적으로 desired를 계산 → 시차 발동 시 desired가 누적.
Step Scaling은 "+1"이라 기존 desired에 상대적 → 최악의 경우에도 1대만 추가.

### ALBRequestCountPerTarget 동작 방식

- `target_value = N` → 인스턴스당 1분간 요청 N개를 목표로 유지
- 계산: `필요 인스턴스 = 총 분당 요청 / target_value`
- target 5,000 기준:

| VU | 분당 요청 | 필요 인스턴스 |
|----|-----------|-------------|
| 30 | ~10,800 | 2대 |
| 50 | ~18,000 | 4대 (max) |
| 80 | ~28,800 | 4대 (max 제한) |

### estimated_instance_warmup = 120초

새 인스턴스 부팅 시: AWS CLI 설치 → git pull → Docker pull → Spring Boot 시작 → Health Check
이 동안 CPU가 일시적으로 높아짐 (JVM 워밍업 등) → warmup 동안 메트릭 제외

---

## 9. t3 버스트 인스턴스

### 개념

t3 인스턴스는 **버스트형**으로, 평소에 CPU 크레딧을 쌓아두고 부하 시 소비한다.

| 항목 | t3.small 기준 |
|------|-------------|
| 베이스라인 CPU | 20% (2 vCPU 중 실질 0.4 vCPU) |
| 크레딧 소진 시 | 베이스라인(20%)으로 성능 제한 |
| 크레딧 충전 | 베이스라인 이하 사용 시 자동 충전 |

### 우리가 겪은 문제

VU 150으로 20분 연속 부하 → 4대 모두 크레딧 소진 → CPU 98% 고정 → 스케일아웃 무의미

```
11:09  크레딧 467 (정상)
11:29  크레딧 149 (급락)
11:34  크레딧 0   (소진 → 성능 제한)
```

### 왜 t3가 부적합한 게 아닌가

FairBid 실제 트래픽 패턴:
- 경매 24/48시간 동안 **간헐적** 입찰
- 종료 직전 **순간적** 트래픽 급증 → 크레딧으로 커버
- k6로 20분 연속 풀 부하는 **실제 트래픽 패턴이 아님**

### 대응

- 부하 테스트: **VU 80으로 감소** (크레딧 소진 방지)
- 운영 환경 고려: `t3.unlimited` (크레딧 소진 후 추가 과금) 또는 `c5.large` (비버스트)

---

## 10. ECR (Elastic Container Registry) & CD 파이프라인

### ECR이란

- AWS의 Docker 이미지 저장소 (Docker Hub의 AWS 버전)
- private 리포지토리로 보안 유지
- IAM 기반 인증

### CD 파이프라인 흐름

```
[GitHub Actions]
  main 브랜치 push/merge
    → Gradle 빌드 → Docker 이미지 빌드
    → ECR push (fairbid-backend:latest)
    → ASG Instance Refresh 트리거

[각 App 인스턴스]
  User Data 실행
    → ECR 로그인 (aws ecr get-login-password)
    → docker pull fairbid-backend:latest
    → docker compose up -d
```

### 이전 방식 (소스 빌드) vs 현재 방식 (ECR pull)

| 항목 | 소스 빌드 (이전) | ECR pull (현재) |
|------|-----------------|----------------|
| 인스턴스 시작 시간 | 3~5분 (Gradle 빌드 포함) | ~30초 |
| 네트워크 의존 | Git clone + Gradle dependencies | ECR pull (~200MB) |
| 빌드 성공 보장 | 인스턴스마다 빌드 → 환경 차이 위험 | CI에서 1회 빌드, 동일 이미지 보장 |

### IAM 설정

- Role: `fairbid-app-role` (EC2 assume)
- Policy: `ssm-read` (SSM GetParameter) + `ecr-pull` (ECR GetAuthorizationToken, BatchGetImage)
- Instance Profile: `fairbid-app-profile`

---

## 11. SSM Parameter Store

### 개념

- AWS의 **설정값 중앙 저장소**
- 키-값 형태로 저장, IAM으로 접근 제어
- 하드코딩 대신 런타임에 조회

### 사용 예시

```bash
# 인프라 서버 IP 저장
aws ssm put-parameter --name "/fairbid/infra-host" --value "172.31.34.73" --type String

# User Data에서 조회
INFRA_HOST=$(aws ssm get-parameter --name "/fairbid/infra-host" \
  --region ap-northeast-2 --query "Parameter.Value" --output text)
```

### 왜 SSM을 쓰는가

| 방식 | 문제 |
|------|------|
| 하드코딩 | IP 변경 시 Launch Template 재생성 필요 |
| AMI에 포함 | AMI 재생성 필요 |
| SSM | **IP 변경 시 SSM 값만 수정** → 새 인스턴스는 자동 반영 |

---

## 12. WebSocket Simple Broker의 한계

### 현재 문제

Spring의 Simple Broker는 **인메모리**로 구독자를 관리한다.
단일 서버에서는 문제없지만, 서버가 2대 이상이 되면 **서버 간 메시지 동기화가 안 된다**.

```
[단일 서버]
서버1: 구독자 [A, B, C] → 입찰 발생 → A, B, C 다 받음 ✅

[오토스케일링 → 2대]
서버1: 구독자 [A, B] → 입찰 발생 → A, B만 받음
서버2: 구독자 [C]   → 모름       → C는 못 받음 ❌
```

### 측정 방법

k6 WebSocket 동기화 테스트로 정량 측정:
1. 경매 1개 생성
2. N명(20명)이 WebSocket으로 구독
3. 1명이 REST API로 입찰
4. 구독자 중 몇 명이 메시지를 수신했는지 측정
5. **수신율 = 수신 유저 / 전체 구독 유저**

기대 결과:
- 서버 1대: 수신율 **100%**
- 서버 2대 이상: 수신율 **< 100%** (동기화 실패 증명)

---

## 13. Sticky Session

### 개념

ALB가 같은 사용자를 **항상 같은 서버로** 보내는 설정. 쿠키 기반.

### 해결되는 것

같은 경매방 구독자가 같은 서버에 붙으므로 메시지 전달 정상 → 수신율 100% 복구

### 한계

| 문제 | 설명 |
|------|------|
| **부하 쏠림** | 인기 경매에 사용자 집중 → 특정 서버 과부하 |
| **SPOF** | 서버 1대 다운 → 해당 서버에 고정된 사용자 전부 끊김 |
| **스케일링 비효율** | 새 서버 추가해도 기존 세션은 안 옮겨짐 |

### 핵심 메시지

> "땜빵은 되는데, 근본 해결이 아니다"

---

## 14. Redis Pub/Sub을 이용한 메시지 동기화

### 동작 원리

Simple Broker 대신 Redis Pub/Sub으로 서버 간 메시지를 릴레이한다.

```
[입찰 발생 — 서버1]
서버1 → Redis Pub/Sub에 메시지 발행
  → 서버1 수신 → 자기 구독자에게 전달
  → 서버2 수신 → 자기 구독자에게 전달
  → 서버N 수신 → 자기 구독자에게 전달
```

### Step 3 vs Step 4 비교

| 지표 | Sticky Session (Step 3) | Redis Pub/Sub (Step 4) |
|------|------------------------|----------------------|
| 메시지 수신율 | 100% | 100% |
| 서버별 CPU 편차 | 쏠림 | 균등 |
| 서버 다운 시 | 해당 경매방 전체 끊김 | 다른 서버로 재연결 |
| Sticky Session | 필요 | **불필요** |
| Stateless | ❌ | **✅** |

### 왜 Kafka가 아닌가

- 이미 Redis 인프라 있음
- 서버 간 브로드캐스팅이 목적 (메시지 영속성 불필요)
- 이 규모에서 Kafka는 오버엔지니어링

---

## 15. k6 부하 테스트

### k6란

- Go 기반 부하 테스트 도구
- JavaScript로 시나리오 작성
- HTTP + WebSocket 지원
- **로컬에서 실행 (무료)**

### 테스트 종류

| 스크립트 | 목적 |
|---------|------|
| `scaleout-bid-test.js` | ASG 스케일링 동작 검증 (VU 80, 20분) |
| `websocket-sync-test.js` | WebSocket 메시지 동기화 수신율 측정 |
| `bid-stress.js` | 입찰 스트레스 테스트 |

### VU(Virtual User) 설정 고려사항

- **VU 80**: 스케일아웃 후 인스턴스당 VU 20~40 → CPU 30~40%로 안정화
- **VU 150+**: t3.small 버스트 크레딧 소진 → CPU 98% 고정 → 의미 없음
- **테스트 시간 20분**: 스케일아웃 파이프라인 5분 + 안정화 관찰 15분

### 커스텀 메트릭 예시

```javascript
const wsConnected = new Counter('ws_connected');         // WS 연결 성공 수
const wsMessageReceived = new Counter('ws_message_received'); // 메시지 수신 수
const wsReceiveRate = new Rate('ws_receive_rate');        // 수신율
```

---

## 16. 삽질 모음 & 교훈

### User Data 관련

| 문제 | 원인 | 해결 |
|------|------|------|
| `$HOME not set` | cloud-init이 root로 실행, HOME 변수 없음 | `git config --system` 사용 |
| `dubious ownership` | git 디렉토리 소유자 ≠ 실행 유저 | `git config --add safe.directory` |
| `${infra_private_ip}` 미치환 | Terraform 변수를 수동 CLI에서 사용 | SSM Parameter Store로 전환 |
| AWS CLI `command not found` | AMI에 AWS CLI 미설치 | User Data 앞부분에 설치 스크립트 추가 |

### Launch Template 관련

| 문제 | 원인 | 해결 |
|------|------|------|
| 코드 변경 후 새 인스턴스 부팅 실패 | Git의 user-data 수정 ≠ AWS Launch Template 갱신 | LT 새 버전 생성 → 기본 버전으로 설정 |
| Instance Refresh InProgress 멈춤 | unhealthy 인스턴스 반복 생성 | `cancel-instance-refresh` → LT 수정 → 재시작 |
| docker-compose-app.yml 변경 후 불일치 | image 방식으로 바꿨는데 user-data는 --build | LT user-data도 ECR pull 방식으로 동기화 |

### 스케일링 관련

| 문제 | 원인 | 해결 |
|------|------|------|
| 스케일아웃 반응 9분 | Redis Lua에서 핵심 처리 → CPU 안 올라감 | RequestCount 기반으로 전환 |
| Target Tracking 2개 과잉 스케일링 | 시차 발동으로 desired 누적 | 보조를 Step Scaling으로 변경 |
| target_value=1000 → 360대 계산 | target이 너무 낮아 항상 max 도달 | target=5,000으로 상향 |
| 4대 확장 후에도 CPU 98% | t3 크레딧 소진 → 베이스라인 20%로 제한 | VU 80으로 감소 |

### 네트워크 관련

| 문제 | 원인 | 해결 |
|------|------|------|
| Redis 외부 연결 실패 | Sentinel이 Docker 내부 IP 전달 | announce-ip로 EC2 IP 알림 |
| ALB 헬스체크 실패 | 보안그룹에서 8080 포트 미허용 | ALB SG → EC2 SG 인바운드 규칙 추가 |
| App → MySQL 연결 실패 | Docker 서비스명은 같은 네트워크에서만 유효 | private IP + 호스트 포트 사용 |

---

## 핵심 교훈 요약

1. **AMI에 뭐가 설치되어 있는지 확인** — AWS CLI 없으면 SSM, ECR 등 AWS 서비스 연동 불가
2. **코드 변경 ≠ 인프라 반영** — user-data 수정해도 Launch Template은 자동 업데이트 안 됨
3. **스케일링 메트릭은 아키텍처에 맞게 선택** — 핵심 로직이 Redis에 있으면 CPU는 무의미
4. **Target Tracking 2개 병행은 위험** — 시차 발동으로 과잉 스케일링
5. **max 제한은 필수** — target_value 실수 시 인스턴스 폭증 방지
6. **t3 버스트 크레딧 이해 필수** — 연속 부하 테스트 시 크레딧 소진으로 성능 제한
7. **Docker 내부 IP vs 외부 IP 구분** — announce-ip 같은 설정 누락 시 외부 통신 불가
8. **Instance Refresh 전에 수동 테스트** — 1대 먼저 띄워서 검증 후 전체 교체
9. **Terraform state와 실제 AWS 리소스 동기화** — 수동으로 만든 리소스는 import하거나 삭제 후 재생성
10. **ALB 경로 기반 라우팅은 SockJS 하위 경로까지 고려** — `/ws`만으로는 부족, `/ws/*`도 필요
11. **정상 종료와 비정상 끊김은 명시적으로 구분** — close 이벤트만으로는 판별 불가

---

## 17. REST/WebSocket 서버 분리

### 왜 분리하냐

모놀리스에서 REST와 WebSocket이 같은 프로세스에 있으면:
- **배포 = WebSocket 끊김**: REST 코드 한 줄 수정해도 전체 프로세스를 재시작해야 하므로 WebSocket 커넥션이 끊김
- **장애 전파**: REST에서 OOM이 터지면 WebSocket도 같이 죽음
- **스케일링 비효율**: REST는 CPU 바운드(요청 처리), WebSocket은 메모리 바운드(커넥션 유지)인데 같은 기준으로 스케일링

### 분리 방식

같은 코드베이스에서 환경변수(`SERVER_ROLE`)로 역할을 분리한다. 멀티 모듈로 코드를 물리적으로 나눌 필요 없음.

```
[같은 Docker 이미지]
├── SERVER_ROLE=api → REST Controller, 스케줄러, Stream 컨슈머, Pub/Sub 발행
├── SERVER_ROLE=ws  → WebSocket Config, Pub/Sub 구독
└── SERVER_ROLE=all → 전부 (로컬 개발용)
```

### 알아야 할 것
- `@Conditional`은 `@Configuration` 클래스 전체의 로딩을 제어한다 — `@EnableWebSocketMessageBroker` 같은 어노테이션도 같이 비활성화됨
- WS 서버에서 `AuctionBroadcastPort` 구현체(`RedisPubSubBroadcastAdapter`)가 없어도 되는 이유: 이 포트를 주입받는 `BidEventListener`도 API 전용이라 같이 비활성화됨
- 빈 의존성 체인을 따라가며 한쪽에서 누락되는 빈이 없는지 확인해야 한다

### 면접 예상 질문

| 질문 | 답변 포인트 |
|------|------------|
| 왜 멀티 모듈로 안 나눴나? | 헥사고날 구조 덕분에 의존성이 이미 깔끔하게 분리되어 있음. 어노테이션 태깅만으로 충분, 멀티 모듈은 빌드/배포 복잡도만 증가 |
| 분리했을 때 메시지 흐름은? | API 서버: 입찰 → Redis Pub/Sub 발행. WS 서버: Redis Pub/Sub 구독 → 로컬 WebSocket 구독자에게 전달 |
| WS 서버가 DB를 안 쓰나? | JPA 초기화에 필요해서 연결은 하지만, 실제 쿼리는 거의 없음. 향후 완전 분리하려면 WS 전용 경량 설정 필요 |

---

## 18. Terraform으로 인프라 관리

### 왜 Terraform이냐

AWS CLI나 셸 스크립트로 인프라를 만들면:
- 다른 계정/환경에서 재현 불가
- 리소스 간 의존 관계 파악 어려움
- 삭제/변경 시 어떤 리소스가 영향받는지 모름

Terraform은 `.tf` 파일에 선언적으로 정의하고, `plan`으로 변경사항을 미리 확인, `apply`로 적용한다.

### 핵심 개념

| 개념 | 설명 |
|------|------|
| `terraform plan` | 현재 상태(state) vs 코드를 비교해서 뭘 만들고/바꾸고/지울지 보여줌 |
| `terraform apply` | plan 결과를 실제로 적용 |
| `terraform state` | Terraform이 관리하는 리소스 목록. 실제 AWS와 동기화해야 함 |
| `terraform import` | 수동으로 만든 AWS 리소스를 Terraform 관리 대상으로 가져옴 |
| `lifecycle.ignore_changes` | 특정 속성 변경을 Terraform이 무시하게 함 (예: AMI 업데이트로 EC2 교체 방지) |

### 삽질 포인트

| 문제 | 원인 | 교훈 |
|------|------|------|
| `terraform apply` 시 인프라 EC2 교체 시도 | `data.aws_ami.ubuntu`가 최신 AMI를 자동 조회 → 기존과 다름 | stateful 리소스는 `ignore_changes = [ami]` 필수 |
| SG description 변경으로 SG 교체 | AWS SG는 description 변경 시 삭제+재생성 (in-place 불가) | import 후 코드를 실제 값에 맞춰야 함 |
| SG 규칙이 state에만 있고 AWS에 없음 | import 형식 오류로 실패했지만 state에 남음 | `state rm` 후 재생성 |
| 수동 리소스와 Terraform 리소스 충돌 | 같은 이름의 TG/ASG가 이미 존재 | 수동 리소스 삭제 후 Terraform으로 통일 |

### 면접 예상 질문

| 질문 | 답변 포인트 |
|------|------------|
| Terraform state가 뭐예요? | 실제 인프라와 코드 사이의 매핑 정보. state가 없으면 Terraform은 전부 새로 만들려고 함 |
| import는 언제 쓰나요? | 수동으로 만든 리소스를 Terraform 관리로 편입할 때. import 후 코드도 실제 설정과 일치시켜야 함 |
| plan에서 destroy가 뜨면? | 이유 확인 필수. AMI 변경, description 불일치 등 사소한 차이로 리소스 교체가 발생할 수 있음 |

---

## 19. ALB 경로 기반 라우팅

### 뭐하는 거냐

하나의 ALB에서 URL 경로에 따라 다른 Target Group으로 보내는 것.

```
[ALB]
├── /ws, /ws/*  → fairbid-websocket-tg (WS 서버)
└── 나머지 전부  → fairbid-rest-tg (REST 서버)
```

### 알아야 할 것

- **리스너 규칙 우선순위**: 숫자가 낮을수록 먼저 매칭. `default`는 항상 마지막
- **SockJS 하위 경로**: SockJS는 `/ws/websocket`, `/ws/info`, `/ws/xxx/websocket` 같은 경로를 사용. `/ws`만 매칭하면 이런 요청이 REST 서버로 감
- **health check 경로**: WS TG도 `/actuator/health`를 사용. WS 서버에서 actuator가 떠야 함

### 면접 예상 질문

| 질문 | 답변 포인트 |
|------|------------|
| 왜 ALB를 2개 안 만들었나? | 비용 절감 + 도메인 하나로 통합. 경로 기반 라우팅이면 충분 |
| WebSocket은 NLB가 낫지 않나? | ALB도 WebSocket 지원함 (HTTP Upgrade). NLB는 L4라 경로 기반 라우팅 불가 |

---

## 20. Spring 빈 조건부 활성화

### 방법들

| 방식 | 사용 예 | 특징 |
|------|--------|------|
| `@Profile("api")` | 프로필별 활성화 | `spring.profiles.active`에 포함되어야 함. 복수 프로필 OR 조건 번거로움 |
| `@ConditionalOnProperty` | 프로퍼티 값 기반 | `havingValue`로 단일 값만 비교. "not equal" 조건 불가 |
| 커스텀 `@Conditional` | 복잡한 조건 | `Condition` 인터페이스 구현. 유연하지만 코드 추가 필요 |

### 우리가 선택한 방식

커스텀 `@EnabledOnRole` 어노테이션 + `ServerRoleCondition`:
- `@EnabledOnRole({"api", "all"})` — api 또는 all일 때 활성화
- `@EnabledOnRole({"ws", "all"})` — ws 또는 all일 때 활성화
- 기본값 `all`이면 모든 빈 활성화 (로컬 개발 환경)

### 왜 `@Profile`을 안 썼나

`@Profile("api")` + `@Profile("all")` 이렇게 쓰면 **둘 다 active**여야 해서 OR 조건이 안 됨. `@Profile({"api", "all"})` 이렇게 하면 OR이긴 한데, `spring.profiles.active=sentinel,load-test`처럼 다른 프로필과 함께 쓸 때 `all`을 항상 넣어야 하는 번거로움이 있음. `server.role`이라는 별도 프로퍼티로 분리하는 게 깔끔.

### 주의점
- `@Conditional`은 `@Configuration` 클래스에 붙이면 해당 클래스의 모든 `@Bean` 메서드도 비활성화됨
- 빈 의존성 체인 확인 필수: A가 B를 주입받는데 B만 비활성화하면 에러
- `@Component`와 `@Configuration` 모두에 적용 가능

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-03-23 | 1.0 | 초안 작성 — 기존 문서 통합 정리 |
| 2026-03-27 | 1.1 | Step 5-2 배경지식 추가 — 서버 분리, Terraform, ALB 라우팅, 빈 조건부 활성화 |
