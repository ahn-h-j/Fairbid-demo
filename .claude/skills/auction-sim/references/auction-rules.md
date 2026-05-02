# FairBid 경매 규칙 (에이전트 참조용)

> 이 문서는 시뮬레이션 에이전트가 경매 비즈니스 규칙을 이해하기 위한 참조 자료다.
> 에이전트는 이 규칙을 바탕으로 자율 판단한다.

---

## 1. 입찰 타입 (bidType)

| 타입 | 동작 | amount 필드 |
|------|------|------------|
| `ONE_TOUCH` | 현재가 + 입찰 단위 자동 계산 (최소 입찰) | 무시됨 |
| `DIRECT` | 금액 직접 지정 (`nextMinBidPrice` 이상) | 필수 |
| `INSTANT_BUY` | `instantBuyPrice`로 즉시 구매 | 무시됨 |

## 2. 입찰 단위 (가격 구간별 차등)

| 현재 가격 | 입찰 단위 |
|-----------|----------|
| 10,000원 미만 | 500원 |
| 10,000 ~ 50,000원 미만 | 1,000원 |
| 50,000 ~ 100,000원 미만 | 3,000원 |
| 100,000 ~ 500,000원 미만 | 5,000원 |
| 500,000 ~ 1,000,000원 미만 | 10,000원 |
| 1,000,000원 이상 | 30,000원 |

## 3. 경매 연장 규칙

- 종료 5분 이내에 입찰이 발생하면 → **5분 자동 연장**
- 연장 횟수 무제한
- **3회 연장마다 입찰 단위가 50% 증가**(서차지)
- 연장 횟수는 응답의 `extensionCount` 필드로 확인 가능

## 4. 즉시구매 규칙

- 경매 등록 시 `instantBuyPrice`를 설정한 경우에만 사용 가능
- **활성화 조건**: `currentPrice < instantBuyPrice × 90%`
- 즉, 현재가가 즉시구매가의 90% 이상이면 비활성화됨
- 활성화 여부는 응답의 `instantBuyEnabled` 필드로 확인
- 발동 시 경매가 `INSTANT_BUY_PENDING` 상태로 전환되고, 1시간의 최종 입찰 기회가 주어짐

## 5. 자기 경매 입찰 금지

- 응답의 `sellerId`가 본인 `userId`와 같으면 절대 입찰하지 말 것
- 시도 시 `SELF_BID_NOT_ALLOWED` 에러

## 6. 첫 입찰 후 수정 불가

- 한 번 입찰하면 그 입찰 자체는 수정 불가
- 더 높은 가격으로 다시 입찰해야 함

## 7. 에러 코드

| 코드 | 의미 | 에이전트 대응 |
|------|------|---------------|
| `BID_TOO_LOW` | 입찰가가 최소 입찰가 미만 | nextMinBidPrice 확인 후 재입찰 또는 포기 |
| `AUCTION_ENDED` | 이미 종료된 경매 | 즉시 다른 경매로 이동 |
| `SELF_BID_NOT_ALLOWED` | 자기 경매 입찰 | sellerId 체크 강화 (이건 실수다!) |
| `INSTANT_BUY_NOT_AVAILABLE` | 즉시구매가 미설정 경매 | DIRECT나 ONE_TOUCH로 전환 |
| `INSTANT_BUY_DISABLED` | currentPrice가 instantBuyPrice의 90% 이상 | DIRECT/ONE_TOUCH로 전환 |
| `INSTANT_BUY_ALREADY_ACTIVATED` | 이미 즉구 발동 상태 | 그 경매는 INSTANT_BUY_PENDING — 다른 경매로 이동 |
| `AUCTION_NOT_FOUND` | 존재하지 않는 경매 ID | ID 잘못 — 목록 다시 조회 |

## 8. 경매 상태 (status)

| 상태 | 의미 |
|------|------|
| `BIDDING` | 입찰 가능 (정상 경매) |
| `INSTANT_BUY_PENDING` | 즉시구매 발동, 1시간 최종 입찰 진행 중 |
| `CLOSED` | 종료 |
| `CANCELLED` | 취소 |

목록 조회 시 `?status=BIDDING`으로 진행 중 경매만 조회 가능.

## 9. 카테고리

`ELECTRONICS`, `FASHION`, `HOME`, `SPORTS`, `HOBBY`, `OTHER`

경매 등록 시 필수 필드.

---

## 10. 낙찰 & 거래 플로우 규칙

### 10.1 경매 종료 시 자동 처리
1. 최고가 입찰자가 **1순위 낙찰자** (rank=1, status=PENDING_RESPONSE)
2. 2순위 후보: 입찰가가 **1순위의 90% 이상**일 때만 생성 (rank=2, status=STANDBY)
3. **Trade 자동 생성**:
   - 경매의 `deliveryAvailable` / `directTradeAvailable`에 따라 method 결정
   - 둘 다 가능 → `AWAITING_METHOD_SELECTION` (구매자가 선택)
   - 한 가지만 가능 → `AWAITING_ARRANGEMENT` (자동 결정)
4. 택배 거래는 DeliveryInfo도 자동 생성 (status=AWAITING_ADDRESS)

### 10.2 응답 기한
- **1순위**: 낙찰 후 **24시간** 내 응답(거래 조율 시작)
- **2순위**: 승계받고 **12시간** 내 응답
- 기한 내 미응답 → 노쇼 처리

### 10.3 노쇼 처리 결과
- 1순위 노쇼 → **경고 +1** (3회 누적 시 계정 차단)
- 2순위가 90%+ 있으면 **자동 승계** (2순위에게 TRANSFER 알림)
- 2순위 없거나 90% 미만 → **유찰** (FAILED)
- 2순위까지 노쇼 → 유찰 (추가 경고 없음)

### 10.4 TradeStatus 전이
```
AWAITING_METHOD_SELECTION → AWAITING_ARRANGEMENT → ARRANGED → COMPLETED
                                                         ↓
                                                    (누군가 complete 호출)
```
중간에 노쇼/취소 → `CANCELLED`

### 10.5 택배 플로우 (DeliveryStatus)
```
AWAITING_ADDRESS
  └ 구매자가 배송지 입력
AWAITING_PAYMENT
  ├ 이 시점부터 구매자에게 sellerBankAccount 노출
  ├ 구매자가 입금 완료 알림 (paymentConfirmed=true)
  └ 판매자가 확인 (paymentVerified=true) 또는 거절 (paymentConfirmed=false로 되돌림)
SHIPPED
  └ 판매자가 송장 입력 (courierCompany, trackingNumber)
DELIVERED
  └ 구매자가 수령 확인 → Trade status → ARRANGED
```

송장 입력 기한: `paymentVerified` 후 **72시간** (`Trade.SHIPPING_DEADLINE_HOURS`).

### 10.6 직거래 플로우 (DirectTradeStatus)
```
PROPOSED (판매자가 시간 제안)
  ├ 구매자 accept → ACCEPTED → Trade ARRANGED
  └ 구매자 counter → COUNTER_PROPOSED
       ├ 판매자 accept → ACCEPTED → Trade ARRANGED
       └ 판매자 counter → COUNTER_PROPOSED (무한 역제안 가능)
```

`meetingDate`는 `@FutureOrPresent` 제약: **오늘 또는 미래만 가능** (과거 불가).

### 10.7 거래 완료
- Trade가 `ARRANGED`일 때 **판매자/구매자 아무나** `POST /trades/{id}/complete` 호출 가능
- 결과: `COMPLETED`, 양쪽에게 TRADE_COMPLETED 알림

### 10.8 시뮬레이션 시간 가속
- 실시간 24h / 12h 기다릴 수 없음
- 오케스트레이터가 `POST /api/v1/test/auctions/{id}/force-noshow`로 노쇼 유발 가능
- 이건 **테스트 목적** — 시뮬에서 일부 경매만 선택적으로 노쇼 유발해서 2순위 승계 플로우 검증

### 10.9 거래 관련 에러 / 권한 규칙
- 배송지 입력, 입금 완료, 수령 확인 → **구매자만**
- 입금 확인/거절, 송장 입력 → **판매자만**
- 거래 완료(`/complete`) → **둘 다 가능**
- 직거래 `propose` 첫 제안 → **판매자**
- `accept`/`counter` → 직전 제안 받은 쪽
- 거래 조회(`/trades/{id}`) → **참여자만** (타인 조회 시도는 권한 에러)

### 10.10 알림 타입 (거래 관련)
`WINNING`, `TRANSFER`, `FAILED`, `SECOND_RANK_STANDBY`, `NO_SHOW_PENALTY`, `RESPONSE_REMINDER`, `METHOD_SELECTED`, `ARRANGEMENT_PROPOSED`, `ARRANGEMENT_COUNTER_PROPOSED`, `ARRANGEMENT_ACCEPTED`, `DELIVERY_ADDRESS_SUBMITTED`, `DELIVERY_SHIPPED`, `PAYMENT_CONFIRMED`, `PAYMENT_VERIFIED`, `PAYMENT_REJECTED`, `TRADE_COMPLETED`
