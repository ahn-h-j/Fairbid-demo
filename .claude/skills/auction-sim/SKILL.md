---
name: auction-sim
description: FairBid AI 에이전트 경매 시뮬레이션 실행. 12개 페르소나 서브에이전트(일반 사용자 8명 + 블랙햇/어그로/폭격기/할머니 4명)가 자율적으로 입찰부터 낙찰 후 거래 완료(택배/직거래/노쇼)까지 진행하면서 비즈니스 로직 엣지케이스와 보안 취약점을 발견하고 최종 리포트를 생성한다. k6 부하 테스트가 발견하지 못하는 사람 같은 행동 패턴 + 악의적 공격 + 거래 플로우 검증용.
disable-model-invocation: false
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, Agent
argument-hint: [없음 — 자동 진행]
---

# FairBid AI 에이전트 경매 시뮬레이션 (오케스트레이터)

너는 오케스트레이터다. 12개 페르소나 서브에이전트를 스폰하고 타임라인을 관리해 결과를 수집한다.

**전체 소요 시간: ~50분** (입찰 20분 → 경매 종료 → 거래 25분 → 수집/리포트 5분)

---

## 페르소나 라인업 (12명)

| 에이전트 | 성격 | 예산 | 역할 |
|---------|-----|------|-----|
| sim-minsu | 소극적 대학생 | 50,000 | 구매 |
| sim-junghyun | 계산적 리셀러 | 500,000 | 구매+판매(시딩) |
| sim-sujin | 충동구매 | 200,000 | 구매 (노쇼 가능성 있음) |
| sim-taeho | 스나이퍼 | 300,000 | 구매 |
| sim-miyoung | 균형형 | 150,000 | 구매+판매(시딩) |
| sim-hyunwoo | 탐색자 | 100,000 | 구매 |
| sim-junhyuk | HOBBY 갑부 | 2,000,000 | 구매 |
| sim-eunji | 신중 첫거래자 | 80,000 | 구매 |
| sim-blackhat | 보안 탐색 | - | 공격 |
| sim-agro | 어그로 판매자 | - | 비정상 경매 등록 |
| sim-flooder | 폭격기 | 300,000 | 연타 입찰 |
| sim-grandma | 할머니 | 100,000 | 실수 폭발 (노쇼 가능성 높음) |

---

## Phase 0: 사전 점검

### 인프라 기동 (Docker Compose)

Docker Desktop이 꺼져 있었다면 **반드시 다음 순서**로 올려라 (역순이면 Redis `NOREPLICAS` 크래시):

```bash
# 1) redis → mysql 먼저
docker compose up -d redis mysql
# 2) Redis replica 제약 해제 (재기동하면 휘발되므로 매번)
docker exec fairbid-sim-redis-1 redis-cli CONFIG SET min-replicas-to-write 0
# 3) backend
docker compose up -d backend
```

백엔드가 `depends_on: redis healthy`로 뜨기 때문에 CONFIG SET 전에 기동되면
BidStreamConsumer init 단계에서 `NOREPLICAS` 로 죽는다. 순서를 지켜야 한다.

### DB 초기화 정책 (중요)

`application-simulation.yml` 에 `spring.jpa.hibernate.ddl-auto: create-drop` 이 박혀 있다. **backend 재기동 = MySQL 스키마 통째로 재생성**이다. 즉:

- 새 시뮬을 시작하려면 **backend를 재기동**하면 깨끗한 상태에서 출발한다.
- Redis 도 컨테이너 재기동으로 같이 휘발돼서 RDB/Redis 정합성이 유지된다.
- 이전 세션의 `auction-ids.txt`, `tokens.jsonl` 등은 **전부 무효**. 재사용 금지.
- 시뮬 도중 "재시딩" 필요하면 backend 재기동이 가장 빠르다. 별도 리셋 엔드포인트는 없다.

```bash
# 백엔드 health check
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health)
# 200 아니면 사용자에게 안내:
# "SPRING_PROFILES_ACTIVE=simulation docker compose up 으로 백엔드를 띄워주세요"

# Mock 로그인 엔드포인트 활성 확인
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"hc@sim.test","nickname":"hc","phoneNumber":"010-0000-0000"}' \
  http://localhost:8080/api/v1/test/auth/login | grep -q accessToken
# 실패 시 simulation 프로파일 미활성 → 종료

# AI 어시스턴트 활성 확인 (선택 — 판매자/블랙햇이 사용)
HC_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"aihc@sim.test","nickname":"aihc","phoneNumber":"010-0000-0001"}' \
  http://localhost:8080/api/v1/test/auth/login | grep -oP '"accessToken":"\K[^"]+')
AI_PROBE=$(curl -s -X POST -H "Authorization: Bearer $HC_TOKEN" -H "Content-Type: application/json" \
  -d '{"category":"ELECTRONICS","memo":"테스트","imageUrls":["https://res.cloudinary.com/demo/image/upload/sample.jpg"]}' \
  http://localhost:8080/api/v1/ai/auction-assist)
AI_ERR=$(echo "$AI_PROBE" | grep -oP '"code":"\K[^"]+' | head -1)
# AI_ERR이 "AI_SERVICE_UNAVAILABLE" 이면 ANTHROPIC_API_KEY 미설정 → 사용자에게 알림
# (계속 진행 가능 — 판매자는 AI 미사용 경매 등록, 블랙햇 AI 공격은 503만 보고)
# "success":true면 AI 활성 — 판매자가 자유롭게 사용 가능

echo "AI 어시스턴트 상태: ${AI_ERR:-AVAILABLE}"

# 작업 디렉토리
TS=$(date +%Y%m%d-%H%M%S)
WORK_DIR="_workspace/auction-sim/$TS"
mkdir -p "$WORK_DIR/logs"
echo "${AI_ERR:-AVAILABLE}" > "$WORK_DIR/ai-status.txt"
```

---

## Phase 1: 인증 (12명)

```bash
PERSONAS=(
  "minsu:민수:010-0001-0001"
  "junghyun:정현:010-0002-0002"
  "sujin:수진:010-0003-0003"
  "taeho:태호:010-0004-0004"
  "miyoung:미영:010-0005-0005"
  "hyunwoo:현우:010-0006-0006"
  "junhyuk:준혁:010-0007-0007"
  "eunji:은지:010-0008-0008"
  "blackhat:태식:010-0009-0009"
  "agro:상도:010-0010-0010"
  "flooder:민철:010-0011-0011"
  "grandma:옥순:010-0012-0012"
)

> "$WORK_DIR/tokens.jsonl"
for entry in "${PERSONAS[@]}"; do
  IFS=':' read -r name nickname phone <<< "$entry"
  RESP=$(curl -s -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"${name}@sim.test\",\"nickname\":\"${nickname}\",\"phoneNumber\":\"${phone}\"}" \
    http://localhost:8080/api/v1/test/auth/login)
  TOKEN=$(echo "$RESP" | grep -oP '"accessToken":"\K[^"]+')
  UID=$(echo "$RESP" | grep -oP '"userId":\K[0-9]+')
  echo "{\"name\":\"$name\",\"token\":\"$TOKEN\",\"userId\":$UID}" >> "$WORK_DIR/tokens.jsonl"
done
```

### Admin 토큰 발급 (SEED_TOKEN)

`/api/v1/test/auctions/**` 조작 엔드포인트(`set-end-time`, `force-close`, `force-noshow`, `winning-status`)는 `ROLE_ADMIN`이 필요하다. Mock 로그인에 `admin:true`를 넣으면 발급된 JWT의 role이 ADMIN으로 박힌다.

```bash
cat > /tmp/seed_login.json <<'EOF'
{"email":"seed@sim.test","nickname":"seed","phoneNumber":"01000000000","admin":true}
EOF
SEED_TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  --data @/tmp/seed_login.json http://localhost:8080/api/v1/test/auth/login \
  | python -c "import json,sys;print(json.load(sys.stdin)['data']['accessToken'])")
echo "SEED_TOKEN=$SEED_TOKEN" > "$WORK_DIR/seed.env"
```

이 SEED_TOKEN은 Phase 2 (`set-end-time`), Phase 4 (`timeline.sh`: force-close / force-noshow / winning-status), Phase 6 (최종 상태 조회)에서 전부 재사용된다.

주의: 일반 페르소나 로그인 때는 `admin` 필드를 빼거나 `false`로 두어라. admin=true를 넣으면 ADMIN 권한까지 붙기 때문에, 시뮬에서 검증하려는 "일반 사용자 시점의 권한 가드"를 건너뛴다.

---

판매자 경매를 일부 등록한 사람에게는 **계좌 정보**도 설정해야 함 (택배 플로우에서 구매자에게 노출되는 데이터 — 없으면 sellerBankAccount가 null). Phase 1 끝 부분에 추가:

```bash
# 정현, 미영, 상도: 판매자 역할 페르소나에게 계좌 설정
for name in junghyun miyoung agro; do
  TOKEN=$(grep "\"name\":\"$name\"" "$WORK_DIR/tokens.jsonl" | grep -oP '"token":"\K[^"]+')
  curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"bankName":"국민은행","accountNumber":"123456-78-901234","accountHolder":"시뮬테스터"}' \
    http://localhost:8080/api/v1/users/me/bank-account > /dev/null
done
```

(엔드포인트가 다르면 UserController 코드 확인 후 맞춰라 — `/api/v1/users/me/bank-account`가 메모리 기반 추정값.)

---

## Phase 2: 정상 경매 시딩 (동기)

`sim-junghyun`과 `sim-miyoung`을 **동기**로 호출해서 정상 경매 각 5개씩, **총 10개** 등록.

AI 어시스턴트가 활성이면(`ai-status.txt`에 `AVAILABLE`) 두 페르소나에게 `GOLDEN_CASES` 경로를 주입. 판매자가 경매 등록 전 AI를 호출해 실시세 기반 추천가를 받고, 동시에 골든 케이스 `expected.low/high`와 대조해서 AI 정확도도 평가한다.

```
GOLDEN_CASES=/c/Users/tkgkd/Desktop/Workspace/FairBid-ai-assist/backend/src/test/resources/ai/golden/cases.jsonl

Agent(
  subagent_type="sim-junghyun",
  description="Seed normal auctions",
  prompt="""
  BASE_URL=http://localhost:8080
  ACCESS_TOKEN={정현 토큰}
  USER_ID={정현 userId}
  LOG_FILE={WORK_DIR}/logs/junghyun-seed.log
  MODE=seed
  AUCTION_COUNT=5
  GOLDEN_CASES=/c/Users/tkgkd/Desktop/Workspace/FairBid-ai-assist/backend/src/test/resources/ai/golden/cases.jsonl

  playbook 참고해서 5개 경매 등록. 다양한 deliveryAvailable/directTradeAvailable 조합 섞어라 (택배/직거래/둘다).
  GOLDEN_CASES가 있고 AI 활성이면 각 경매마다 한 줄 랜덤 pick해서 AI 어시스턴트 호출 후 추천가 사용 + expected.low/high 대조 기록.
  완료 후 {"auctionIds":[...]}.
  """
)
```

`sim-miyoung`도 동일 (`GOLDEN_CASES` 포함).

### 시간 가속 (경매 종료 시각을 22분 후로)

```bash
END_AT_BID_EPOCH=$(($(date +%s) + 1200))  # 20분 후 입찰 종료
END_AT_SIM_EPOCH=$(($(date +%s) + 3000))  # 50분 후 시뮬 종료

# 경매 종료를 22분 후로 (오케스트레이터가 20분 시점에 force-close할 거라 여유 2분)
for AID in $SEEDED_AUCTION_IDS; do
  curl -s -X POST -H "Authorization: Bearer $SEED_TOKEN" \
    "http://localhost:8080/api/v1/test/auctions/$AID/set-end-time?seconds=1320" > /dev/null
done
```

`$SEEDED_AUCTION_IDS`: 정현+미영 응답에서 수집한 id 목록. 공백 구분 문자열.

---

## Phase 3: 12명 병렬 스폰 (END_AT_SIM_EPOCH까지 살아있음)

전 에이전트에게 `END_AT_EPOCH=$END_AT_SIM_EPOCH` 주입. 에이전트는 50분 동안 자율적으로 **입찰 → 거래 단계 전환** 다 판단함.

```
Agent(
  subagent_type="sim-minsu",
  description="Run minsu",
  prompt="""
  BASE_URL=http://localhost:8080
  ACCESS_TOKEN={민수 토큰}
  USER_ID={민수 userId}
  LOG_FILE={WORK_DIR}/logs/minsu.log
  END_AT_EPOCH={END_AT_SIM_EPOCH}

  너의 페르소나(.claude/agents/sim-minsu.md)와 playbook
  (.claude/skills/auction-sim/references/agent-playbook.md)에 따라
  자율 행동해라.

  시뮬은 2단계:
  1. 입찰 단계 — 경매가 BIDDING 상태 (처음 ~20분)
  2. 거래 단계 — 오케스트레이터가 force-close하면 네 낙찰 확인하고 거래 진행

  너가 거래 진행을 안 하면 노쇼됨 (그게 너의 판단이면 OK).

  완료 시 playbook의 완료 응답 JSON으로 응답.
  """,
  run_in_background=true
)
```

**블랙햇 추가 환경변수**:
```
TARGET_USER_IDS=<다른 userId 11개 콤마구분>
```
tokens.jsonl에서 본인 제외한 11명 userId 뽑아서 전달.

12개 task ID 수집.

---

## Phase 4: 오케스트레이터 타임라인 스크립트 (백그라운드)

에이전트가 살아있는 동안 오케스트레이터가 중간에 force-close와 force-noshow를 트리거해야 함. 타임라인 쉘 스크립트 만들어서 백그라운드로 실행.

```bash
cat > "$WORK_DIR/timeline.sh" <<'TIMELINE'
#!/usr/bin/env bash
WORK_DIR="__WORK_DIR__"
SEED_TOKEN="__SEED_TOKEN__"
AUCTION_IDS="__AUCTION_IDS__"     # 공백 구분
LOG="$WORK_DIR/timeline.log"
BASE=http://localhost:8080

echo "[$(date +%T)] timeline start" >> "$LOG"

# 20분 후: 모든 경매 강제 종료 → 낙찰/Trade 자동 생성
sleep 1200
echo "[$(date +%T)] force-close all auctions" >> "$LOG"
for AID in $AUCTION_IDS; do
  curl -s -X POST -H "Authorization: Bearer $SEED_TOKEN" \
    "$BASE/api/v1/test/auctions/$AID/force-close" >> "$LOG" 2>&1
  echo "" >> "$LOG"
done

# 15분 더 기다림 (총 35분 시점, 거래 진행할 시간 줌)
sleep 900

# 그 후, 아직 Trade가 진행 중(ARRANGED 아님)인 경매 중 절반에 force-noshow
echo "[$(date +%T)] check trades for noshow candidates" >> "$LOG"
NOSHOW_CANDIDATES=()
for AID in $AUCTION_IDS; do
  STATUS_RESP=$(curl -s -H "Authorization: Bearer $SEED_TOKEN" \
    "$BASE/api/v1/test/auctions/$AID/winning-status")
  TRADE_STATUS=$(echo "$STATUS_RESP" | grep -oP '"afterTradeStatus":"\K[^"]+' | head -1 \
    || echo "$STATUS_RESP" | grep -oP '"status":"\K[^"]+' | head -1)
  case "$TRADE_STATUS" in
    AWAITING_METHOD_SELECTION|AWAITING_ARRANGEMENT)
      NOSHOW_CANDIDATES+=("$AID")
      ;;
  esac
done
echo "[$(date +%T)] noshow candidates: ${NOSHOW_CANDIDATES[*]}" >> "$LOG"

# 절반만 노쇼 유발 (2순위 승계 플로우 검증용)
HALF=$((${#NOSHOW_CANDIDATES[@]} / 2))
for ((i=0; i<HALF; i++)); do
  AID="${NOSHOW_CANDIDATES[$i]}"
  echo "[$(date +%T)] force-noshow $AID" >> "$LOG"
  curl -s -X POST -H "Authorization: Bearer $SEED_TOKEN" \
    "$BASE/api/v1/test/auctions/$AID/force-noshow" >> "$LOG" 2>&1
  echo "" >> "$LOG"
done

echo "[$(date +%T)] timeline done" >> "$LOG"
TIMELINE

# 플레이스홀더 치환
sed -i "s|__WORK_DIR__|$WORK_DIR|g" "$WORK_DIR/timeline.sh"
sed -i "s|__SEED_TOKEN__|$SEED_TOKEN|g" "$WORK_DIR/timeline.sh"
sed -i "s|__AUCTION_IDS__|$SEEDED_AUCTION_IDS|g" "$WORK_DIR/timeline.sh"
chmod +x "$WORK_DIR/timeline.sh"
```

이 `timeline.sh`를 **run_in_background: true**로 실행:

```bash
Bash(command="bash $WORK_DIR/timeline.sh", run_in_background=true)
```

백엔드 호출 세 번(force-close 10번, winning-status 10번, force-noshow 5번)만 하면 되므로 별도 에이전트 필요 없음. Bash 백그라운드로 충분.

---

## Phase 5: 완료 대기 + 결과 수집

12개 에이전트 task + timeline.sh task의 완료 알림 대기. 폴링하지 말고 알림을 기다려라.

각 에이전트 task 완료 시 `TaskOutput`으로 JSON 회수 → `$WORK_DIR/results.jsonl`.
`timeline.sh`는 특별한 응답 없음 — 로그만 `$WORK_DIR/timeline.log`에서 참고.

---

## Phase 6: 최종 상태 조회

```bash
# 각 경매의 최종 Winning/Trade 상태
> "$WORK_DIR/final-states.jsonl"
for AID in $SEEDED_AUCTION_IDS; do
  curl -s -H "Authorization: Bearer $SEED_TOKEN" \
    "http://localhost:8080/api/v1/test/auctions/$AID/winning-status" \
    >> "$WORK_DIR/final-states.jsonl"
  echo "" >> "$WORK_DIR/final-states.jsonl"
done

# 어그로가 추가 등록한 경매도 수집 (최신 경매 목록에서 seeded 제외한 것)
curl -s -H "Authorization: Bearer $SEED_TOKEN" \
  "http://localhost:8080/api/v1/auctions?status=CLOSED" \
  > "$WORK_DIR/all-closed-auctions.json"
```

---

## Phase 7: 리포트 생성

이 시뮬은 **두 산출물**을 생성한다:
1. `$WORK_DIR/report.md` — **회차 단독** raw 발견 (아래 Phase 7-A)
2. `_workspace/auction-sim/findings.md` — **회차 누적** 비교 매트릭스 + 결함별 상세 카드 (아래 Phase 7-B)

회차별 raw 추적은 1번에, 면접/PR에서 꺼낼 종합본은 2번에서 본다.

---

## Phase 7-A: 회차 단독 리포트 → `$WORK_DIR/report.md`

```markdown
# FairBid AI 에이전트 경매 시뮬레이션 리포트

- **실행 시각**: {TS}
- **소요 시간**: ~50분
- **참여자**: 12명
- **시딩 경매**: N개 (정상 10 + 어그로 M)

---

## 1. 에이전트별 요약

| 에이전트 | 입찰(성공/실패) | 낙찰 | Trade 참여 | Trade 완료 | 노쇼 | 예산 사용률 | 발견 취약점 |
|---------|---------------|------|-----------|-----------|-----|----------|----------|
| 민수 | 3/2 | 0 | 0 | 0 | 0 | 0% | - |
| 정현 | ... | ... | ... | ... | ... | ... | ... |
| ... | ... | ... | ... | ... | ... | ... | ... |

## 2. 경매별 결과

| 경매 ID | 카테고리 | 시작가 | 낙찰가 | 입찰수 | 연장 | 1순위 | 1순위 상태 | 2순위 | Trade | 비고 |
|---------|---------|-------|-------|-------|-----|------|----------|-----|-------|-----|
| 1 | ELECTRONICS | 50,000 | 87,000 | 12 | 3 | 정현 | RESPONDED | 수진(STANDBY) | COMPLETED | 정상 거래 |
| 3 | FASHION | 30,000 | 45,000 | 8 | 0 | 수진 | NO_SHOW | 민수(PENDING_RESPONSE) | AWAITING_ARRANGEMENT | 2순위 승계 |
| 7 | HOBBY | 20,000 | 40,000 | 5 | 0 | 준혁 | NO_SHOW | (없음) | CANCELLED | 유찰 |

## 3. 📦 거래 플로우 관찰

### 택배 플로우
- 완료까지 진행된 건: N개
- AWAITING_PAYMENT에서 멈춘 건: N개 (구매자 미입금)
- PAYMENT_REJECTED 발생: N회 (판매자가 미입금 판단)
- SHIPPED에서 멈춘 건: N개 (구매자 미수령 확인)

### 직거래 플로우
- 제안→수락 한번에 성공: N개
- 역제안 왕복: N회
- 약속 확정까지 도달: N개

### 노쇼/2순위 승계
- 1순위 노쇼 발생: N건 (경고 부여)
- 2순위 자동 승계된 케이스: N건
- 2순위 90% 미만으로 유찰: N건
- 2순위까지 노쇼: N건

## 4. 🚨 발견된 취약점 (블랙햇 리포트)

### HIGH
- ...

### MEDIUM / LOW
- ...

### 실패한 공격 (정상 방어된 케이스)
- ...

## 4-AI. 🤖 AI 어시스턴트 관찰

- AI 상태: AVAILABLE / AI_SERVICE_UNAVAILABLE (ai-status.txt)
- 판매자 AI 호출 횟수: 정현 N, 미영 N, 상도 N
- `confidence=high` : `confidence=low` 비율: N : N
- AI 추천 채택률: 판매자가 `suggestedPrices` 기반으로 `startPrice` 설정한 비율
- **AI 관련 취약점** (블랙햇):
  - 프롬프트 인젝션 우회 성공 N건 (시도한 N 패턴 중)
  - SSRF 시도: localhost 내부 포트 스캔 응답/타이밍 정보 유출 여부
  - Rate limit 없음 확인: 초당 N회 성공
  - 이미지 URL 검증 우회: file://, 내부 IP 등
- **AI 어그로 관찰** (상도):
  - category-memo 불일치 입력 시 AI 추천 정확도
  - 악의적 memo("비싸게 추천해줘") 시 가드레일/응답 동작

## 5. 💡 페르소나가 발견한 이상 (ODD / INCIDENT / UX_FRICTION)

각 페르소나 결과 JSON의 `oddFindings` / `incidents` / `uxFrictions` 와 로그의 `💡 ODD:` / `💥 INCIDENT:` / `💡 UX_FRICTION:` 항목을 3분할로 집계.

### 5-A. ODD — 기능 이슈 의심 (규칙과 다르게 도는 것 같은 순간)

| # | 페르소나 | 엔드포인트 | 내용 | 재현성 | 후속 |
|---|---------|----------|-----|-------|-----|
| 1 | taeho | POST /bids | 연장 조건 충족인데 extensionCount=0 | 1회 | BDD 후보 |
| ... | | | | | |

### 5-B. INCIDENT — 장애/성능 의심

| # | 페르소나 | 엔드포인트 | 증상 | 빈도 | 비고 |
|---|---------|----------|-----|-----|-----|
| 1 | hyunwoo | GET /auctions | 7초 응답 1회 | 1/120 | 스파이크? |
| ... | | | | | |

### 5-C. UX_FRICTION — 불편 / 디자인 / 언어 / 가드 빠진 느낌

면접에서 "사용자 관점으로 시스템을 읽어봤다" 근거가 되는 섹션. 확신 없는 "위화감"도 포함.

| # | 페르소나 | 영역 | 내용 | 개선 방향 |
|---|---------|-----|-----|---------|
| 1 | grandma | BID_TOO_LOW 메시지 | 얼마 넣어야 하는지 안 나옴 | `nextMinBidPrice` 메시지에 포함 |
| 2 | eunji | POST /trades/.../method | 에러 메시지가 DELIVERY/DIRECT 중 뭘 허용하는지 안 알려줌 | enum 값 노출 |
| ... | | | | |

## 6. ⚠️ 엣지케이스

- 종료 5분 내 입찰로 연장 발동: N회
- INSTANT_BUY 발동: N회
- 5명 이상 경쟁: N건
- 할머니 실수 패턴: N회
- 폭격기 race condition 시도: N회

## 7. 흥미로운 로그 발췌

각 페르소나 로그에서 인상적인 구간 10~20줄.

### 민수
...

### 태식 (블랙햇)
...

### 옥순 (할머니)
...

### 수진 (충동구매 → 노쇼)
...

## 8. 탐색 다양성 지표 (페르소나 프롬프트 품질)

LLM 페르소나가 **"구체 지시를 따르는 로봇"**이 아니라 **자율적으로 탐색**했는지를 가늠한다.

- 전체 API 호출 중 `(엔드포인트 + 파라미터 패턴)` 중복 비율
  - 동일 persona 내 중복률 / 전체 persona 교차 중복률
- 블랙햇 공격 벡터 고유성: `vulnsFound[].type` 의 종 수 / `totalProbes`
- 어그로 ODD_BEHAVIOR 고유성: 같은 이상 케이스 반복 vs 새 각도 개척

중복률이 높으면 페르소나 프롬프트에 구체 액션 리스트가 남아있다는 신호다 — 다음 라운드 전에 `.claude/agents/sim-*.md` 를 다시 축소한다.

## 9. 기존 테스트 커버리지 분석 (시뮬의 실제 가치)

발견된 이슈 각각이 **기존 단위/인수 테스트로 이미 잡히는 경로인지**를 분류한다. 이 표가 "시뮬이 있어야 했던 이유"를 숫자로 설명한다.

| # | 발견 내용 | 심각도 | 기존 테스트 커버 여부 | 비고 |
|---|----------|-------|-------------------|-----|
| 1 | AI 추천가 골든케이스 대비 과소평가 | MEDIUM | ❌ (단위 테스트에 시세 비교 없음) | C 범주 유일 확보 건 |
| 2 | ... | ... | ✅/❌ | ... |

- **N = 발견 총 건수**, **M = ❌(기존 테스트로 잡히지 않던) 건수**
- 면접 답변의 "실제로 N건 발견, 그중 M건은 기존 테스트가 커버 못 하던 경로" 수치가 여기서 나온다.

---

## 권장 후속 작업

- 발견된 HIGH 취약점 즉시 수정
- ❌ 표시 이슈들을 **Cucumber BDD 시나리오로 고정** (시뮬이 찾고 → BDD가 지킨다 프레임)
- 중복률 높았던 페르소나 프롬프트 추가 축소
```

---

## Phase 7-B: 누적 발견 갱신 → `_workspace/auction-sim/findings.md`

회차별 raw 발견(`report.md`)을 **회차 누적 비교 매트릭스 + 결함별 상세 카드**에 반영한다. 다음 회차에서 같은 결함을 새로 발견하면 재현 카운트가 자동으로 올라간다.

### 갱신 절차

1. `_workspace/auction-sim/findings.md`가 있으면 읽기. 없으면 새로 생성.
2. 이번 회차 `report.md`를 검토하면서 다음을 분류:
   - 기존 카드(예: T-1 Long.MAX, H-1 ship→COMPLETED)에 **재현 증거 추가** → 회차 카운트 ++
   - 기존 N=2 카드(2+3 또는 1+3)에 이번 회차 재현 시 → **N=3 정식 발견으로 승격** (T-* 섹션으로 이동)
   - 1·2 회차에 없던 **신규 결함** → "회차별 재현 매트릭스 G. 단독" 섹션에 추가
   - **신규 CRITICAL/HIGH/MEDIUM**은 §2 "결함별 상세 카드" 신규 카드 작성 (메커니즘 / 실서비스 영향 / 악용 시나리오 / 수정 방향)
3. §0 "회차 개요" 표에 새 회차 행 추가, 누적 통계 갱신
4. §3 "정상 동작 검증"에 이번 회차 정상 방어된 항목 추가 (V-* 행)
5. §5 "회차별 시뮬 가치 추세" 표에 새 회차 컬럼 추가
6. §6 "다음 회차 우선순위" 갱신 — 이번 회차 단독 발견 중 다음 회차 재검증 대상 명시

### 절대 규칙

- **"면접 답변 템플릿" 섹션 추가 금지** — 발견 자체(메커니즘/영향/악용/수정)만 담는다. 면접 톤은 사용자가 보고 직접 푼다.
- **결함별 상세 카드는 CRITICAL/HIGH 위주** — MEDIUM도 핵심만, LOW는 표로 압축. 카드 인플레이션 금지.
- **시뮬 환경 격리/조건부 발견은 §4에 정직히 분류** — admin:true(simulation 격리), Actuator 노출(internal-only 분리 시 무효), SSRF(fetch 코드 미확인) 등.
- 회차 카운트 업데이트 시 증거 셀(예: "3회차: hyunwoo 405k→415k")까지 구체적으로 적는다 — 다음 회차에서 검증 가능해야 한다.

### 산출물 톤 (참고)

§0 회차 개요, §1 재현 매트릭스 7개 섹션(A 1·2·3 / B 2+3 / C 1+2 / D 1+3 / E 1만 / F 2만 / G 3만), §2 결함별 카드, §3 정상 동작 검증, §4 시뮬 한계, §5 가치 추세, §6 다음 회차 우선순위.

---

완료 후 사용자에게:
> 시뮬레이션 완료:
> - 회차 raw: `$WORK_DIR/report.md` — 취약점 HIGH N개, 이상 동작 N개, 노쇼 M건
> - 누적 발견: `_workspace/auction-sim/findings.md` — N=3 정식 K건, 누적 총 L건
> 신규 단독 발견 N건은 다음 회차에서 재검증 대기.

---

## 절대 규칙

- 사용자가 "중단해" → 즉시 12개 task + timeline task `TaskStop`
- 백엔드 simulation 프로파일 아니면 진행 금지
- 운영 DB 금지 (localhost:8080 전용)
- 시뮬 도중 백엔드 코드 수정 금지

---

## 트러블슈팅

| 증상 | 원인 | 대응 |
|------|------|------|
| `/test/auth/login` 404 | simulation 프로파일 미활성 | 백엔드 재시작 |
| 에이전트 401 | 토큰 미주입/만료 | tokens.jsonl 재확인 |
| `sellerBankAccount`가 null | 판매자가 계좌 미등록 | Phase 1 계좌 설정 루프 확인 |
| Trade 조회 시 `method`가 null | AWAITING_METHOD_SELECTION 상태 | 구매자가 `/method` 선택 API 먼저 호출 필요 |
| direct/propose 400 | meetingDate가 과거 | `date +%F`로 오늘 날짜 사용 |
| timeline.sh 안 돎 | 백그라운드 실행 실패 | `ps` / `bash $WORK_DIR/timeline.sh &` 수동 |
| 모든 Trade가 `CANCELLED` | force-noshow 과다 | timeline.sh의 `HALF=` 조정 |
