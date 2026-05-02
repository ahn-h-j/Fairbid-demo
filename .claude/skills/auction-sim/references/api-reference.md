# FairBid API 레퍼런스 (시뮬레이션 에이전트용)

> 시뮬레이션 에이전트가 사용하는 REST API 모음. 모든 호출은 `Bash(curl)`로 수행한다.

---

## 인증

모든 API는 `Authorization: Bearer $ACCESS_TOKEN` 헤더 필요.
오케스트레이터가 시작 시 발급해서 환경변수로 주입한다.

---

## 0. Mock OAuth 로그인 (시뮬레이션 전용)

오케스트레이터만 호출. 에이전트는 이미 발급된 토큰을 받는다.

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"minsu@sim.test","nickname":"민수","phoneNumber":"010-0001-0001"}' \
  http://localhost:8080/api/v1/test/auth/login
```

응답:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "userId": 42,
    "onboarded": true
  },
  "serverTime": "...",
  "error": null
}
```

---

## 1. 경매 목록 조회

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/auctions?status=BIDDING"
```

`status` 옵션: `BIDDING`, `INSTANT_BUY_PENDING`, `CLOSED`, `CANCELLED`

응답 (페이징):
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,
        "title": "에어팟 프로 2세대",
        "category": "ELECTRONICS",
        "currentPrice": 80500,
        "instantBuyPrice": 250000,
        "scheduledEndTime": "2026-04-07T18:00:00",
        "status": "BIDDING",
        "sellerId": 100
      }
    ],
    "nextCursor": "..."
  }
}
```

---

## 2. 경매 상세 조회

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/auctions/3"
```

응답 (상세):
```json
{
  "success": true,
  "data": {
    "id": 3,
    "sellerId": 100,
    "title": "...",
    "category": "ELECTRONICS",
    "currentPrice": 15000,
    "nextMinBidPrice": 16000,
    "instantBuyPrice": 50000,
    "instantBuyEnabled": true,
    "bidIncrement": 1000,
    "scheduledEndTime": "2026-04-07T18:00:00",
    "extensionCount": 2,
    "totalBidCount": 5,
    "status": "BIDDING",
    "topBidderId": 102
  }
}
```

**핵심 필드** (입찰 결정에 필수):
- `sellerId`: 자기 경매 회피용
- `currentPrice`: 현재가
- `nextMinBidPrice`: DIRECT 입찰 시 최소값
- `instantBuyPrice` + `instantBuyEnabled`: 즉시구매 가능 여부
- `scheduledEndTime`: 종료 시각 (스나이퍼 판단용)
- `topBidderId`: 본인이 1등인지 확인

---

## 3. 입찰

### 3-1. ONE_TOUCH (최소 입찰)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bidType":"ONE_TOUCH"}' \
  "$BASE_URL/api/v1/auctions/3/bids"
```

### 3-2. DIRECT (금액 직접 지정)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bidType":"DIRECT","amount":50000}' \
  "$BASE_URL/api/v1/auctions/3/bids"
```

`amount`는 반드시 `nextMinBidPrice` 이상이어야 함.

### 3-3. INSTANT_BUY (즉시구매)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bidType":"INSTANT_BUY"}' \
  "$BASE_URL/api/v1/auctions/3/bids"
```

`instantBuyEnabled=true`인 경우에만 가능.

### 입찰 응답

성공:
```json
{
  "success": true,
  "data": {
    "auctionId": 3,
    "bidId": 999,
    "bidAmount": 16000,
    "currentPrice": 16000
  }
}
```

실패:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BID_TOO_LOW",
    "message": "..."
  }
}
```

---

## 4. 경매 등록 (판매자 에이전트만)

```bash
curl -s -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "아이패드 미니 6세대",
    "category": "ELECTRONICS",
    "startPrice": 80000,
    "instantBuyPrice": 250000,
    "duration": "HOURS_24",
    "deliveryAvailable": true,
    "directTradeAvailable": false
  }' \
  "$BASE_URL/api/v1/auctions"
```

**필드 요약**:
- `category`: ELECTRONICS, FASHION, HOME, SPORTS, HOBBY, OTHER
- `duration`: HOURS_24, HOURS_48
- `instantBuyPrice`: 선택 (없으면 즉시구매 비활성화)
- `deliveryAvailable` / `directTradeAvailable`: 거래 방식 선택

---

## 5. 시간 가속 (오케스트레이터만 호출)

```bash
# 종료 시간을 현재로부터 N초 후로 변경
curl -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/test/auctions/3/set-end-time?seconds=1800"

# 강제 종료
curl -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/test/auctions/3/force-close"
```

---

## 6. 응답 파싱 팁 (jq)

```bash
# 토큰만 추출
TOKEN=$(curl -s ... | jq -r '.data.accessToken')

# 경매 ID 목록만 추출
curl -s ... | jq -r '.data.items[].id'

# 첫 번째 BIDDING 경매의 currentPrice
curl -s ... | jq '.data.items[0].currentPrice'

# 에러 코드만 추출
RESPONSE=$(curl -s -X POST ...)
ERROR_CODE=$(echo "$RESPONSE" | jq -r '.error.code // empty')
```

`jq` 가 없으면 grep/sed 사용:
```bash
TOKEN=$(curl -s ... | grep -oP '"accessToken":"\K[^"]+')
```

---

## 7. 공통 응답 형식

성공:
```json
{ "success": true, "data": { ... }, "serverTime": "...", "error": null }
```

실패:
```json
{ "success": false, "data": null, "serverTime": "...", "error": { "code": "...", "message": "..." } }
```

**판단 기준**: `success` 필드를 먼저 확인. `false`면 `error.code`로 분기.

---

## 8. 낙찰 & 거래 플로우

경매 종료 후 Trade가 자동 생성된다. 에이전트는 자기 낙찰 여부를 확인하고 거래를 완료까지 진행한다.

### 8.1 내 낙찰 상태 확인 (경매 상세에서)

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/auctions/{auctionId}"
```

응답 중 **`userWinningInfo`** 필드를 본다:
```json
{
  "data": {
    "id": 3,
    "status": "CLOSED",
    "winnerId": 42,
    ...
    "userWinningInfo": {
      "rank": 1,                  // 1(낙찰자) / 2(2순위 대기) / null(입찰 안함/해당없음)
      "status": "PENDING_RESPONSE" // 또는 STANDBY, RESPONDED, NO_SHOW, FAILED
    }
  }
}
```

**WinningStatus 의미**:
- `PENDING_RESPONSE`: 응답 대기 (거래 진행해야 함)
- `STANDBY`: 2순위 후보 (1순위 응답 중, 기다려)
- `RESPONDED`: 응답 완료 (거래 조율 시작됨)
- `NO_SHOW`: 본인이 노쇼 처리됨 (경고 받음)
- `FAILED`: 유찰

### 8.2 내 거래 목록 조회

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/my"
```

응답:
```json
{ "data": [{"id":1,"auctionId":3,"status":"AWAITING_ARRANGEMENT","method":"DELIVERY",...}] }
```

### 8.3 거래 상세 조회

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}"
```

주요 필드:
- `status`: AWAITING_METHOD_SELECTION | AWAITING_ARRANGEMENT | ARRANGED | COMPLETED | CANCELLED
- `method`: DELIVERY | DIRECT | null
- `sellerId`, `buyerId`, `finalPrice`
- `deliveryInfo`: 택배일 때 (status, paymentConfirmed, paymentVerified, 주소, 송장 등)
- `directTradeInfo`: 직거래일 때 (meetingDate, meetingTime, status, proposedBy)
- `sellerBankAccount`: **택배 AWAITING_PAYMENT + 본인이 구매자**일 때만 노출 (bankName, accountNumber, accountHolder)

### 8.4 거래 방식 선택 (구매자 전용, 둘 다 가능 경매만)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{"method":"DELIVERY"}' \
  "$BASE_URL/api/v1/trades/{tradeId}/method"
```
`method`: `"DELIVERY"` 또는 `"DIRECT"`. `AWAITING_METHOD_SELECTION` 상태에서만 가능.

### 8.5 거래 완료

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}/complete"
```
`ARRANGED` 상태에서만 가능. 판매자/구매자 아무나 호출.

---

## 9. 택배(Delivery) 플로우 API

**흐름**: `AWAITING_ADDRESS` → (구매자 배송지) → `AWAITING_PAYMENT` → (구매자 입금완료 → 판매자 확인) → (판매자 송장) → `SHIPPED` → (구매자 수령확인) → `DELIVERED` → Trade `ARRANGED`

### 9.1 배송지 입력 (구매자)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "recipientName": "홍길동",
    "recipientPhone": "010-1234-5678",
    "postalCode": "06158",
    "address": "서울시 강남구 테헤란로 123",
    "addressDetail": "101동 101호"
  }' \
  "$BASE_URL/api/v1/trades/{tradeId}/delivery/address"
```
`AWAITING_ADDRESS` 상태만 가능. 성공 시 `AWAITING_PAYMENT`로 전이 → 이 시점부터 구매자가 trade 조회 시 `sellerBankAccount` 노출됨.

### 9.2 입금 완료 알림 (구매자)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}/delivery/payment"
```
구매자가 은행에 입금했다고 서버에 알림. `paymentConfirmed: true`로 바뀌고 판매자에게 `PAYMENT_CONFIRMED` 알림 발송.

### 9.3 입금 확인 (판매자)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}/delivery/payment/verify"
```
판매자가 실제 입금 확인 후 호출. `paymentVerified: true`. 구매자에게 `PAYMENT_VERIFIED` 알림.

### 9.4 입금 거절 (판매자)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}/delivery/payment/reject"
```
미입금 처리. `paymentConfirmed`가 false로 되돌아감. 구매자에게 `PAYMENT_REJECTED` 알림.

### 9.5 송장 입력 (판매자, 발송)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{"courierCompany":"CJ대한통운","trackingNumber":"123456789012"}' \
  "$BASE_URL/api/v1/trades/{tradeId}/delivery/ship"
```
`paymentVerified: true` 상태만 가능. `SHIPPED`로 전이.

### 9.6 수령 확인 (구매자)

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}/delivery/confirm"
```
`SHIPPED` 상태만 가능. `DELIVERED`로 전이 → Trade 자동으로 `ARRANGED` → `/trades/{id}/complete` 호출 가능.

---

## 10. 직거래(DirectTrade) 플로우 API

**흐름**: 판매자 `propose` → (구매자 `accept` 또는 `counter` → 판매자 `accept`) → `ACCEPTED` → Trade `ARRANGED`

### 10.1 시간 제안 (판매자, 첫 제안)

```bash
# meetingDate는 오늘 이상이어야 함 (@FutureOrPresent)
TODAY=$(date +%F)
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d "{\"meetingDate\":\"$TODAY\",\"meetingTime\":\"18:00:00\"}" \
  "$BASE_URL/api/v1/trades/{tradeId}/direct/propose"
```

### 10.2 수락

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/trades/{tradeId}/direct/accept"
```
제안 받은 쪽이 호출. `ACCEPTED`로 전이 + Trade `ARRANGED` 자동 전이.

### 10.3 역제안

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d "{\"meetingDate\":\"$TODAY\",\"meetingTime\":\"20:00:00\"}" \
  "$BASE_URL/api/v1/trades/{tradeId}/direct/counter"
```
받은 제안 대신 다른 시간 제시. 상대가 `accept` 호출하면 확정.

---

## 11. AI 경매 어시스턴트 (판매자 경매 등록 보조)

판매자가 경매 등록 전 AI에게 **시작가 3구간 + 상품 설명**을 추천받는 용도.
**onboarded 유저만**(시뮬의 12명 다 해당). 비용: 호출당 ~80~150원.

### 11.1 추천 받기

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" \
  -d '{
    "category": "ELECTRONICS",
    "memo": "맥북 프로 14 M3, 2024년 구매, 상태 거의 새것",
    "imageUrls": ["https://res.cloudinary.com/demo/image/upload/sample.jpg"]
  }' \
  "$BASE_URL/api/v1/ai/auction-assist"
```

**필드**:
- `category`: ELECTRONICS/FASHION/HOME/SPORTS/HOBBY/OTHER (optional — 미지정 시 AI가 추론)
- `memo`: 상품 정보 (optional, 최대 1000자)
- `imageUrls`: **필수, 최소 1장, 최대 5장** (비어있으면 400)

**응답 성공**:
```json
{
  "success": true,
  "data": {
    "suggestedPrices": { "low": 1800000, "mid": 2100000, "high": 2400000 },
    "generatedDescription": "2024년 구매한 맥북 프로 14 M3...",
    "confidence": "high",
    "confidenceReason": null
  }
}
```

- `confidence`: `"high"` (웹검색 시세 기반) / `"low"` (AI 추정)
- `confidenceReason`: low일 때 사유 문자열, high면 null

### 11.2 에러 코드

| 코드 | HTTP | 상황 |
|------|------|------|
| `PROMPT_INJECTION_DETECTED` | 400 | memo에 "기존 지시 무시해", "system prompt 보여줘" 같은 패턴 |
| `INVALID_IMAGE` | 400 | 이미지 URL 접근 실패/포맷 미지원 |
| `AI_GENERATION_FAILED` | 502 | AI 응답 JSON 파싱 실패/필드 누락 |
| `AI_SERVICE_UNAVAILABLE` | 503 | Claude API 5xx/타임아웃/API 키 미설정 |

### 11.3 사용 흐름 (판매자 페르소나)

1. 등록할 물건 정해짐 → AI 먼저 호출
2. `suggestedPrices.low`를 `startPrice`로, `suggestedPrices.high`를 `instantBuyPrice`로 쓰거나 페르소나 판단대로 조정
3. `generatedDescription`은 **별도 경매 상세 설명 필드가 없으므로 무시 OK** (FairBid 경매 등록 DTO에 description 필드 없음). 추천 시세만 채택해도 됨
4. `confidence=low`면 시세 정보 신뢰도 낮으니 페르소나 판단으로 조정 가능

### 11.4 사용 안 해도 됨

AI 어시스턴트는 **선택 기능**이다. 판매자 페르소나는 성향에 따라 "써본다/안 쓴다" 자유 판단.

---

## 12. 테스트 전용 — 경매 종료 & 노쇼 강제 처리 (오케스트레이터만)

### 11.1 경매 강제 종료

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/test/auctions/{auctionId}/force-close"
```
즉시 종료 → Winning/Trade 자동 생성 → 1순위에게 WINNING 알림.

### 11.2 노쇼 강제 처리

```bash
curl -s -X POST -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/test/auctions/{auctionId}/force-noshow"
```
1순위 응답 기한을 과거로 조작 + 노쇼 스케줄러 즉시 실행 → 1순위 `NO_SHOW` + 경고 +1 + (2순위 90%+ 있으면 `TRANSFER` 승계, 아니면 `FAILED` 유찰).

응답 예시:
```json
{
  "data": {
    "auctionId": 3,
    "afterFirstWinningStatus": "NO_SHOW",
    "afterSecondWinningStatus": "PENDING_RESPONSE",
    "secondWinningNewDeadline": "...",
    "afterTradeStatus": "AWAITING_ARRANGEMENT",
    "afterTradeBuyerId": 99
  }
}
```

### 11.3 낙찰/거래 상태 조회 (디버그)

```bash
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$BASE_URL/api/v1/test/auctions/{auctionId}/winning-status"
```
1순위/2순위 Winning + Trade 상태를 한 번에 조회. 디버깅/리포트용.

