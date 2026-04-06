# Redis 고가용성 (HA) Specification

> ⚠️ **이 문서는 향후 진행할 테스트의 계획서입니다. 아직 실행 전이며, 실행 후 별도 결과 문서를 작성할 예정입니다.**

> Redis가 Source of Truth인 구조에서 SPOF를 인식하고, 장애 시뮬레이션을 통해 Sentinel 기반 고가용성 구성을 검증한다.

---

## 1. Overview

### Problem Statement

Redis가 입찰의 핵심 데이터(현재가, 입찰 검증, 순위)를 처리하는 Source of Truth인데, 단일 인스턴스로 운영 중:

```
[현재 구조]
Redis: 단일 인스턴스 → 죽으면 입찰 서비스 전체 마비
```

> Stream/Consumer HA(Consumer Group, 멱등성)는 Issue #62에서 별도 다룸.
> RDB는 이력 백업 용도로, Replication은 현재 스코프에서 제외. 향후 필요 시 별도 이슈로 진행.

### Solution Summary

```
[목표 구조]
Redis: Sentinel (Master 1 + Slave 2 + Sentinel 3)
→ 자동 failover + Split Brain 방지 + 클라이언트 자동 재연결
```

### Why Sentinel, Not Cluster?

| 기준 | Sentinel | Cluster |
|------|----------|---------|
| 데이터 규모 | 단일 노드로 충분 (경매 데이터 < 수 GB) | 수십 GB 이상 샤딩 필요 시 |
| 운영 복잡도 | 낮음 (Master-Slave + 감시) | 높음 (슬롯 관리, 리밸런싱) |
| Lua 스크립트 | 제약 없음 | 키가 같은 슬롯에 있어야 함 |
| 적합한 상황 | HA만 필요 | HA + 수평 확장 둘 다 필요 |

**결론**: 데이터 규모가 작고 Lua 스크립트(bid.lua)를 핵심으로 사용하므로 Sentinel이 적정 기술.
Cluster는 "데이터 규모가 커서 샤딩이 필요할 때" 도입하는 것이지, HA만을 위해 쓰면 오버엔지니어링.

### Success Criteria

| 기준 | 목표 |
|------|------|
| Master 장애 | Sentinel이 자동 failover, 5-10초 내 복구 |
| Split Brain | 고립된 Master가 쓰기 거부 (데이터 분기 방지) |
| 네트워크 파티션 | 프로세스 생존 + 통신 불가 상황에서도 정상 failover |
| 클라이언트 | failover 후 자동 재연결, 일시적 에러 최소화 |

---

## 2. 테스트 환경

- **로컬 Docker** (docker-compose)
- 장애 주입: `docker kill`, `docker pause`, `docker network disconnect`
- 부하 테스트: k6

---

## 3. Redis Sentinel HA (풀코스)

### Step 1: 현재 상태 확인

**목표**: SPOF 인식 — Redis 단일 인스턴스가 죽으면 서비스가 어떻게 되는지 체감

**시나리오**:
```bash
# Redis 강제 종료
docker kill redis-master

# 서비스 요청
curl -X POST http://localhost:8080/api/bids
```

**확인 사항**:
- 서비스 터지는지 확인
- 에러 로그 확인

**예상 결과**: 서비스 완전 중단

---

### Step 2: Replication 구성

**목표**: Master-Slave 구성, 수동 failover 체험 → "자동화가 필요하다"는 동기 부여

**구성**:
```yaml
# docker-compose.yml
services:
  redis-master:
    image: redis:7
    ports:
      - "6379:6379"

  redis-slave-1:
    image: redis:7
    ports:
      - "6380:6379"
    command: redis-server --replicaof redis-master 6379

  redis-slave-2:
    image: redis:7
    ports:
      - "6381:6379"
    command: redis-server --replicaof redis-master 6379
```

**시나리오**:
```bash
# 데이터 동기화 확인
redis-cli -p 6379 SET test "hello"
redis-cli -p 6380 GET test

# Master 종료
docker kill redis-master

# Slave 수동 승격
redis-cli -p 6380 REPLICAOF NO ONE
```

**확인 사항**:
- 데이터 동기화 여부
- 수동 승격 과정
- 앱 재연결 필요 여부

**예상 결과**: 수동 failover 가능하지만 번거로움 체감

---

### Step 3: Persistence 설정

**목표**: 재시작 시 데이터 복구 확인

**구성**:
```
# redis.conf
appendonly yes
appendfsync everysec
```

**시나리오**:
```bash
# 데이터 입력
redis-cli SET bid:123 "data"

# Redis 강제 종료 + 재시작
docker kill redis-master
docker start redis-master

# 데이터 확인
redis-cli GET bid:123
```

**확인 사항**:
- 재시작 후 데이터 존재 여부
- 유실 범위 (AOF everysec 기준 최대 1초)

**예상 결과**: 대부분 데이터 복구, 일부 유실 가능

---

### Step 4: Sentinel 구성

**목표**: 자동 failover 구성

**구성**:
```yaml
# docker-compose.yml
services:
  redis-master:
    image: redis:7

  redis-slave-1:
    image: redis:7
    command: redis-server --replicaof redis-master 6379

  redis-slave-2:
    image: redis:7
    command: redis-server --replicaof redis-master 6379

  sentinel-1:
    image: redis:7
    ports:
      - "26379:26379"
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf

  sentinel-2:
    image: redis:7
    ports:
      - "26380:26379"
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf

  sentinel-3:
    image: redis:7
    ports:
      - "26381:26379"
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf
```
```
# sentinel.conf
sentinel monitor mymaster redis-master 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

**시나리오**:
```bash
# Master 강제 종료
docker kill redis-master

# Sentinel 로그 확인
docker logs sentinel-1

# 새 Master 확인
redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

**확인 사항**:
- failover 자동 발생 여부
- failover 소요 시간
- Sentinel 로그 (감지 → 투표 → 승격)

**예상 결과**: 5-10초 내 자동 failover

---

### Step 4-1: Split Brain 방지

**목표**: Master 2개 되는 상황 방지

**배경**:
```
네트워크 단절 시 Sentinel이 새 Master 승격
→ 원래 Master도 살아있으면 Master 2개
→ 데이터가 두 군데로 갈라짐 (Split Brain)
```

**구성**:
```
# redis.conf (Master)
min-replicas-to-write 1
min-replicas-max-lag 10
```
→ Slave 1개 이상 연결 안 되면 쓰기 거부

**시나리오**:
```bash
# Master와 Slave 사이 네트워크 끊기
docker network disconnect fairbid_network redis-slave-1
docker network disconnect fairbid_network redis-slave-2

# Master에 쓰기 시도
redis-cli -p 6379 SET test "hello"
```

**확인 사항**:
- Master가 쓰기 거부하는지 (NOREPLICAS 에러)
- 클라이언트가 에러 받는지

**예상 결과**: Master가 고립되면 쓰기 거부 → Split Brain 방지

---

### Step 4-2: 네트워크 파티션 장애

**목표**: 프로세스 죽음이 아닌 네트워크 단절 상황 테스트

**배경**:
```
docker kill = 프로세스 깔끔하게 종료 (비현실적)
network disconnect = 프로세스 살아있는데 통신 불가 (현실적)
```

**시나리오**:
```bash
# Master 네트워크만 끊기 (프로세스는 살아있음)
docker network disconnect fairbid_network redis-master

# Sentinel 로그 확인 (sdown → odown → failover)
docker logs -f sentinel-1

# 새 Master 확인
redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster

# 네트워크 복구
docker network connect fairbid_network redis-master
```

**확인 사항**:
- Sentinel이 감지하는 시간 (down-after-milliseconds)
- failover 완료까지 시간
- docker kill과 비교해서 차이점

**예상 결과**: docker kill보다 감지 시간 더 걸림 (타임아웃 대기)

---

### Step 4-3: 구 Master 복구 시 동작

**목표**: failover 후 원래 Master 재시작 시 정상 동작 확인

**시나리오**:
```bash
# 1. 현재 Master 확인
redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster

# 2. Master 종료 → failover 발생
docker kill redis-master

# 3. 새 Master에 데이터 쓰기
redis-cli -h <새 Master IP> SET after-failover "new-data"

# 4. 구 Master 재시작
docker start redis-master

# 5. 구 Master 역할 확인
redis-cli -p 6379 INFO replication
# role:slave 여야 정상

# 6. 구 Master에서 데이터 확인
redis-cli -p 6379 GET after-failover
```

**확인 사항**:
- 구 Master가 자동으로 Slave가 되는지
- 새 Master의 데이터가 동기화되는지
- 수동 개입 필요 여부

**예상 결과**: Sentinel이 자동으로 구 Master를 Slave로 재구성

---

### Step 5: 클라이언트 튜닝

**목표**: failover 중 앱이 어떻게 동작하는지 확인하고, 에러를 최소화하는 설정 구성

#### 5-1. Spring Boot Sentinel 연동

**구성**:
```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - 172.22.0.20:26379
          - 172.22.0.21:26379
          - 172.22.0.22:26379
      timeout: 3000ms
```

#### 5-2. Lettuce 클라이언트 설정

```java
@Configuration
@Profile("sentinel")
public class RedisSentinelConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> builder
            .readFrom(ReadFrom.MASTER)
            // 모든 읽기/쓰기를 Master에서 처리 (Slave의 stale 데이터 방지)
            .clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(
                    ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                // 연결 끊기면 즉시 에러 → 빠른 실패 (대기 X)
                .timeoutOptions(TimeoutOptions.builder()
                    .fixedTimeout(Duration.ofSeconds(3))
                    .build())
                .build());
    }
}
```

**핵심 설정 의도**:
| 설정 | 값 | 이유 |
|------|---|------|
| `@Profile("sentinel")` | - | sentinel 프로필 활성화 시에만 적용 (개발 환경 유연성) |
| `ReadFrom.MASTER` | MASTER | 경매 데이터의 비동기 복제 지연으로 인한 stale 읽기 방지 |
| `autoReconnect` | true | failover 후 새 Master로 자동 재연결 |
| `disconnectedBehavior` | REJECT_COMMANDS | 끊긴 상태에서 명령 대기 X → 즉시 에러 |
| `fixedTimeout` | 3초 | 커넥션/커맨드 타임아웃 상한 |

#### 5-3. 읽기 분산 (미적용)

> `ReadFrom.REPLICA_PREFERRED`로 읽기를 Slave에 분산할 수 있지만,
> 경매 시스템 특성상 비동기 복제 지연으로 인한 stale 데이터(이전 입찰가, 잘못된 낙찰자 등)
> 위험이 있어 `ReadFrom.MASTER`로 설정하여 모든 읽기/쓰기를 Master에서 처리한다.

#### 5-4. 부하 테스트 중 failover 시나리오

**시나리오**:
```bash
# 부하 테스트 중 Master 종료
k6 run scripts/bid-load.js &
docker kill redis-master
```

**측정 항목**:
| 지표 | 측정 방법 |
|------|----------|
| failover 중 에러 건수 | HTTP 5xx 카운트 |
| failover 중 에러 지속 시간 | 첫 에러 ~ 마지막 에러 타임스탬프 |
| 재연결 자동 여부 | failover 후 정상 응답 복구 확인 |
| p95 응답시간 변화 | Baseline vs failover 구간 |

**예상 결과**: failover 5-10초 동안 일시적 에러 후 자동 복구

---

### Step 6: 모니터링

**목표**: 장애 시 관찰 포인트 정리

**수집 지표**:
```bash
# Redis INFO
redis-cli INFO replication
redis-cli INFO memory
redis-cli INFO stats

# Sentinel 상태
redis-cli -p 26379 SENTINEL master mymaster
```

**확인 사항**:
- Master/Slave 상태
- 메모리 사용량
- 커넥션 수
- replication lag

---

## 4. 구현 계획

### 4.1 브랜치 전략

```
main
└── feat/ha-redis-sentinel    # Redis Sentinel HA (전체)
```

### 4.2 구현 순서

```
[Redis Sentinel HA]
├── Step 1: 현재 상태 확인 — SPOF 체감
├── Step 2: Replication 구성 — 수동 failover 체험
├── Step 3: Persistence 설정 — 데이터 복구 확인
├── Step 4: Sentinel 구성 — 자동 failover
│   ├── 4-1: Split Brain 방지
│   ├── 4-2: 네트워크 파티션 테스트
│   └── 4-3: 구 Master 복구 동작
├── Step 5: 클라이언트 튜닝 — Lettuce 설정 + 부하 중 failover
└── Step 6: 모니터링 — 관찰 포인트 정리
```

---

## 5. 결과 기록 형식

### 5.1 단계별 기록

```markdown
# Step N: {단계명}

## 구성
- docker-compose / config 파일

## 장애 시뮬레이션
- 주입 방법
- 명령어

## 결과
| 지표 | 값 |
|------|---|
| failover 시간 | Nms |
| 에러율 | N% |
| ... | ... |

## 로그 / 스크린샷

## 분석
- 뭘 했고
- 뭐가 일어났고
- 왜 그런지
```

---

## 6. 포트폴리오 스토리라인

### 6.1 전체 흐름

```
[SPOF 인식]
Redis가 Source of Truth인데 단일 인스턴스 → 죽으면 서비스 터짐
→ 장애 시뮬레이션으로 증명 (Step 1)

[단계별 해결]
Replication → "수동 failover 가능하지만 새벽 3시에 직접 해야 함"
Persistence → "재시작 후 데이터는 살아있지만 failover는 여전히 수동"
Sentinel → "자동 failover + Split Brain 방지까지"
→ 각 단계에서 불편함 체감 후 다음 단계 필요성 이해

[클라이언트까지]
Sentinel만으로 끝이 아님
→ Lettuce 재연결 전략, 읽기 분산, 타임아웃 설정까지 해야 실제 서비스에서 동작
→ 부하 테스트로 failover 중 에러율/복구 시간 실측
```

### 6.2 면접 예상 질문

| 질문 | 답변 포인트 |
|------|------------|
| Redis Sentinel이 뭐예요? | 자동 failover 감시 프로세스. 구성: Sentinel 3대(과반 투표), Master 1 + Slave 2. failover 시간: 실측 N초 |
| failover 중 요청은 어떻게 돼요? | Lettuce가 Sentinel에서 새 Master 주소 받아 자동 재연결. 그 사이 N초간 에러 발생, `disconnectedBehavior=REJECT_COMMANDS`로 빠른 실패 유도 |
| 왜 Cluster 안 썼어요? | 데이터 < 수 GB로 샤딩 불필요. Lua 스크립트(bid.lua)가 핵심인데 Cluster는 키 슬롯 제약 있음. HA만 필요하면 Sentinel이 적정 기술 |
| Split Brain 어떻게 방지해요? | `min-replicas-to-write 1` — Slave 연결 없으면 쓰기 거부. 고립된 구 Master에 데이터 쓰이는 거 방지 |
| failover 후 구 Master 켜면요? | Sentinel이 자동으로 Slave로 재구성. 새 Master 데이터 동기화. 수동 개입 불필요 |
| 읽기 분산은 어떻게요? | `ReadFrom.REPLICA_PREFERRED` — 읽기는 Slave 우선, failover 중에도 조회 가능. 쓰기(Lua)는 항상 Master |

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
| 2025-02-02 | 1.0 | 초안 작성 |
| 2026-02-05 | 1.1 | 계획 문서임을 명시, 예상vs현실 섹션 추가 |
| 2026-02-11 | 2.0 | Redis Sentinel 전용으로 스코프 축소 (Stream/Consumer·RDB 제거), Step 5 클라이언트 튜닝 구체화 (Lettuce 설정, 읽기 분산, 측정 항목), Sentinel vs Cluster 선택 근거 추가 |
