# 오토스케일링 인프라 구성 학습 가이드

> 이 문서는 FairBid 오토스케일링 구성 과정에서 사용한 AWS 리소스와 개념을 정리한 것이다.
> 면접에서 "직접 구성했다"고 말하려면 아래 내용을 이해하고 있어야 한다.

---

## 1. ALB (Application Load Balancer)

### 뭐하는 거냐
- 여러 App 서버 앞에서 **요청을 분배**하는 로드밸런서
- 클라이언트는 ALB 주소로 요청 → ALB가 뒤에 있는 서버 중 하나로 전달

### 구성 요소

```
[클라이언트] → [ALB] → [Target Group] → [App 서버 1]
                                       → [App 서버 2]
                                       → [App 서버 N]
```

| 개념 | 설명 |
|------|------|
| Load Balancer | 요청을 받아서 뒤로 분배하는 진입점. DNS 이름이 부여됨 |
| Listener | ALB가 어떤 포트/프로토콜로 요청을 받을지 정의 (예: HTTP 80) |
| Target Group | 요청을 받을 서버(타겟)들의 그룹. 헬스체크 설정 포함 |
| Health Check | Target Group이 서버가 살아있는지 주기적으로 확인 (예: `/actuator/health`) |

### 우리가 한 것
```bash
# 1. Target Group 생성 (App 서버 8080, 헬스체크 /actuator/health)
aws elbv2 create-target-group --name fairbid-app-tg --port 8080 ...

# 2. ALB 생성 (최소 2개 AZ 서브넷 필요)
aws elbv2 create-load-balancer --name fairbid-alb --subnets subnet-a subnet-c ...

# 3. Listener 생성 (80 포트 → Target Group으로 전달)
aws elbv2 create-listener --port 80 --default-actions Type=forward,TargetGroupArn=...
```

### 알아야 할 것
- ALB는 **최소 2개 AZ(가용 영역)의 서브넷**이 필요하다
- ALB 자체에도 **보안그룹**이 필요하다 (인바운드 80/443 허용)
- App 서버 보안그룹에서 **ALB 보안그룹으로부터 8080 인바운드**를 허용해야 한다
- Target Group의 헬스체크가 실패하면 ALB가 해당 서버로 요청을 안 보낸다

---

## 2. ASG (Auto Scaling Group)

### 뭐하는 거냐
- EC2 인스턴스를 **자동으로 늘리고 줄이는** 서비스
- CPU가 높아지면 서버 추가, 낮아지면 서버 제거

### 구성 요소

```
[ASG]
├── Launch Template: "어떤 서버를 만들 건지" (AMI, 인스턴스 타입, 보안그룹, user-data)
├── Scaling Policy: "언제 늘리고 줄일지" (CPU 50% 넘으면 추가)
├── Min/Max/Desired: 최소 1대, 최대 4대, 현재 목표 1대
└── Target Group 연결: 새 인스턴스가 뜨면 자동으로 ALB에 등록
```

| 개념 | 설명 |
|------|------|
| Launch Template | 인스턴스 생성 템플릿 (AMI, 타입, 키페어, 보안그룹, 시작 스크립트) |
| AMI | 서버 이미지 스냅샷. 현재 서버 상태를 그대로 복제할 수 있음 |
| User Data | 인스턴스 시작 시 자동 실행되는 스크립트 (초기 설정, 앱 시작 등) |
| Scaling Policy | 스케일 아웃/인 조건 (Target Tracking: CPU 평균 50% 유지) |
| Instance Refresh | 기존 인스턴스를 새 Launch Template 버전으로 교체하는 작업 |
| Health Check Grace Period | 인스턴스 시작 후 N초 동안 헬스체크 면제 (앱 부팅 시간 고려) |

### 우리가 한 것
```bash
# 1. 현재 EC2에서 AMI 생성 (Docker + 코드 포함)
aws ec2 create-image --instance-id i-xxx --name fairbid-app-20260307

# 2. Launch Template 생성 (AMI + user-data 스크립트)
aws ec2 create-launch-template --launch-template-name fairbid-app-lt ...

# 3. ASG 생성 (min:1, max:4, ALB Target Group 연결)
aws autoscaling create-auto-scaling-group --min-size 1 --max-size 4 ...

# 4. CPU 기반 스케일링 정책 추가 (평균 CPU 50% 초과 시 스케일 아웃)
aws autoscaling put-scaling-policy --policy-type TargetTrackingScaling ...
```

### 알아야 할 것
- ASG가 새 인스턴스를 만들면 **자동으로 Target Group에 등록** → ALB가 요청 분배
- 헬스체크 실패하면 ASG가 인스턴스를 **자동으로 종료하고 새로 생성** (자가 치유)
- Launch Template을 **버전 관리**할 수 있다 (v1 → v2 → v3)
- Instance Refresh로 기존 인스턴스를 **새 버전으로 롤링 교체** 가능

---

## 3. User Data (EC2 시작 스크립트)

### 뭐하는 거냐
- EC2 인스턴스가 **처음 시작될 때 자동 실행**되는 bash 스크립트
- ASG에서 새 인스턴스가 뜰 때마다 실행됨

### 우리가 쓴 스크립트
```bash
#!/bin/bash
# 1. git 설정 (cloud-init 환경에서 $HOME이 없어서 --system 사용)
git config --system --add safe.directory /home/ubuntu/Fairbid

# 2. 최신 코드 pull
cd /home/ubuntu/Fairbid
git pull origin main

# 3. 기존 컨테이너 정리 (AMI에 남아있는 것들)
docker stop $(docker ps -aq)
docker rm $(docker ps -aq)

# 4. 환경변수 설정
echo "INFRA_HOST=172.31.34.73" >> .env

# 5. App만 실행
docker compose -f infra/scaleout/docker-compose-app.yml --env-file .env up -d --build
```

### 삽질했던 것들
| 문제 | 원인 | 해결 |
|------|------|------|
| `$HOME not set` | cloud-init이 root로 실행하는데 HOME 환경변수가 없음 | `git config --system` 사용 (HOME 불필요) |
| `dubious ownership` | git 디렉토리 소유자와 실행 유저가 다름 | `git config --add safe.directory` |
| docker-compose-app.yml not found | git pull 실패해서 새 파일이 없음 | 위 두 문제 해결 후 자연히 해결 |

### 알아야 할 것
- User Data는 **root 권한**으로 실행되지만, cloud-init 환경이라 일반적인 셸과 다름
- `base64` 인코딩해서 Launch Template에 전달
- 디버깅은 `/var/log/cloud-init-output.log`에서 확인
- 인스턴스 **최초 시작 시에만** 실행됨 (재부팅 시에는 안 됨, 별도 설정 필요)

---

## 4. Security Group (보안그룹)

### 뭐하는 거냐
- EC2/ALB의 **방화벽**. 어떤 IP/포트에서 들어오는 트래픽을 허용할지 정의

### 우리가 만든 보안그룹

```
[fairbid-alb-sg] - ALB용
├── 인바운드: 80 (HTTP) ← 0.0.0.0/0 (전세계)
└── 인바운드: 8080 ← 0.0.0.0/0

[fairbid-sg] - EC2용 (기존 + 추가)
├── 인바운드: 22 (SSH) ← 0.0.0.0/0
├── 인바운드: 80 ← 0.0.0.0/0
├── 인바운드: 8080 ← ALB 보안그룹 (sg-xxx)     ← 추가
├── 인바운드: 3306 (MySQL) ← 172.31.0.0/16 (VPC 내부)  ← 추가
├── 인바운드: 6379-6381 (Redis) ← 172.31.0.0/16         ← 추가
└── 인바운드: 26379-26381 (Sentinel) ← 172.31.0.0/16    ← 추가
```

### 알아야 할 것
- **Source에 보안그룹 ID를 지정**할 수 있다 → "ALB에서 오는 트래픽만 허용"
- **VPC CIDR(172.31.0.0/16)** 지정 → 같은 VPC 내 인스턴스 간 통신 허용
- 보안그룹은 **stateful** → 인바운드 허용하면 응답(아웃바운드)은 자동 허용
- 포트를 안 열면 ALB 헬스체크도 실패한다 (우리가 삽질한 부분)

---

## 5. Redis Sentinel 외부 연결 (announce-ip)

### 문제
- Redis Sentinel이 Master 주소를 **Docker 내부 IP(172.22.0.10)**로 알려줌
- 외부 App 서버에서는 Docker 내부 IP에 접근 불가

### 해결 구조

```
[기존: 단일 서버, Docker 내부 통신]
Sentinel → "Master는 172.22.0.10:6379" → App (같은 Docker 네트워크) → 접근 가능

[스케일아웃: 외부 서버 통신]
Sentinel → "Master는 172.22.0.10:6379" → 외부 App 서버 → 접근 불가!

[해결: announce-ip 설정]
Sentinel → "Master는 172.31.34.73:6379" → 외부 App 서버 → EC2 IP로 접근 가능
```

### 설정한 것들

| 대상 | 설정 | 용도 |
|------|------|------|
| Redis Master | `--replica-announce-ip ${INFRA_HOST}` | Sentinel에 자신의 외부 IP 알림 |
| Redis Slave | `--replica-announce-ip ${INFRA_HOST} --replica-announce-port 6380` | 외부에서 Slave 접근 시 EC2 IP+포트 사용 |
| Sentinel | `sentinel announce-ip ${INFRA_HOST}` | Sentinel 자신의 외부 IP 알림 |
| Sentinel conf | `sentinel monitor mymaster ${INFRA_HOST} 6379 2` | Master를 EC2 IP로 모니터링 |

### 알아야 할 것
- `replica-announce-ip`는 Redis가 **자신의 주소를 외부에 알릴 때** 사용하는 IP
- Docker 내부에서는 컨테이너명이나 내부 IP로 통신하지만, **외부에서는 호스트 IP + 매핑된 포트**로 접근
- Sentinel의 `sentinel monitor`에 적힌 IP가 **외부 클라이언트에 그대로 전달**됨
- Slave의 announce-port는 **Docker 호스트에 매핑된 포트** (6380, 6381)를 써야 함

---

## 6. 인프라/App 분리 (docker-compose 분리)

### 왜 분리하냐
- 오토스케일링은 **App 서버만** 늘려야 한다
- DB/Redis가 같이 들어있으면 서버마다 별도 DB가 생겨서 의미 없음

### 분리 구조

```
[인프라 EC2 - 고정 1대]
docker-compose-infra.yml
├── MySQL (3306)
├── Redis Master (6379)
├── Redis Slave ×2 (6380, 6381)
├── Sentinel ×3 (26379, 26380, 26381)
├── Prometheus (9595)
└── Grafana (3001)

[App EC2 - ASG로 자동 확장]
docker-compose-app.yml
└── Backend (8080)
    ├── DB → INFRA_HOST:3306
    ├── Redis → INFRA_HOST:6379
    └── Sentinel → INFRA_HOST:26379,26380,26381
```

### 핵심 환경변수
| 변수 | 용도 | 값 |
|------|------|---|
| `INFRA_HOST` | 인프라 서버 private IP | 172.31.34.73 |
| `REDIS_SENTINEL_NODES` | Sentinel 노드 주소 | ${INFRA_HOST}:26379,${INFRA_HOST}:26380,${INFRA_HOST}:26381 |
| `SPRING_DATASOURCE_URL` | DB 연결 URL | jdbc:mysql://${INFRA_HOST}:3306/fairbid |

### 알아야 할 것
- Docker 서비스명(`mysql`, `redis`)은 **같은 docker-compose 네트워크 내에서만** 사용 가능
- 외부 서버에서 연결할 때는 **EC2 private IP + 호스트에 매핑된 포트** 사용
- private IP는 **같은 VPC 내에서만** 접근 가능 (보안그룹 허용 필요)

---

## 7. 전체 아키텍처 (현재 상태)

```
[클라이언트]
     │
     ▼
[ALB: fairbid-alb]  ← HTTP 80
     │
     ▼
[Target Group: fairbid-app-tg]  ← 헬스체크 /actuator/health
     │
     ├── [App EC2 #1] ← ASG가 자동 관리
     │     └── Backend (8080)
     │           ├── MySQL → 172.31.34.73:3306
     │           └── Redis Sentinel → 172.31.34.73:26379~26381
     │
     └── (부하 시 App EC2 #2, #3, #4 자동 추가)

[인프라 EC2: 172.31.34.73]  ← 고정
     ├── MySQL (3306)
     ├── Redis Sentinel (Master + Slave×2 + Sentinel×3)
     ├── Prometheus (9595)
     └── Grafana (3001)
```

---

## 8. 면접 예상 질문

| 질문 | 핵심 답변 |
|------|----------|
| ALB와 NLB 차이는? | ALB는 L7(HTTP), NLB는 L4(TCP). WebSocket은 ALB가 지원. NLB는 더 빠르지만 HTTP 라우팅 불가 |
| ASG에서 헬스체크가 실패하면? | 해당 인스턴스 terminate → 새 인스턴스 생성 (자가 치유) |
| User Data는 언제 실행되나? | 인스턴스 최초 부팅 시 1회. 재부팅 시에는 안 됨 |
| private IP vs public IP? | private IP는 VPC 내부 통신용 (고정), public IP는 외부 접근용 (인스턴스 재시작 시 변경) |
| 왜 DB/Redis를 ASG에 안 넣었나? | DB는 stateful이라 스케일 아웃 불가. 서버마다 별도 DB가 생기면 데이터 불일치 |
| announce-ip가 뭐냐? | Redis가 클라이언트에 자신의 주소를 알릴 때 쓰는 IP. Docker 내부 IP 대신 호스트 IP를 알리도록 설정 |

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-03-07 | 1.0 | 초안 작성 |
